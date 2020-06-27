package com.hardcodecoder.pulsemusic.activities;

import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.os.Bundle;
import android.transition.Fade;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textview.MaterialTextView;
import com.hardcodecoder.pulsemusic.GlideApp;
import com.hardcodecoder.pulsemusic.GlideConstantArtifacts;
import com.hardcodecoder.pulsemusic.R;
import com.hardcodecoder.pulsemusic.TaskRunner;
import com.hardcodecoder.pulsemusic.adapters.DetailsAdapter;
import com.hardcodecoder.pulsemusic.dialog.RoundedBottomSheetDialog;
import com.hardcodecoder.pulsemusic.helper.MediaArtHelper;
import com.hardcodecoder.pulsemusic.helper.UIHelper;
import com.hardcodecoder.pulsemusic.interfaces.LibraryItemClickListener;
import com.hardcodecoder.pulsemusic.loaders.ItemsLoader;
import com.hardcodecoder.pulsemusic.model.MusicModel;
import com.hardcodecoder.pulsemusic.singleton.TrackManager;
import com.hardcodecoder.pulsemusic.utils.DimensionsUtil;

import java.util.List;
import java.util.Locale;

public class DetailsActivity extends MediaSessionActivity implements LibraryItemClickListener {

    public static final String KEY_ITEM_CATEGORY = "category";
    public static final String ALBUM_ID = "id";
    public static final String KEY_TITLE = "title";
    public static final String KEY_ART_URL = "art";
    public static final String KEY_TRANSITION_NAME = "transition";
    public static final int CATEGORY_ALBUM = 1;
    public static final int CATEGORY_ARTIST = 2;
    private String title;
    private List<MusicModel> mList;
    private TrackManager tm;
    private Long mAlbumId;
    private int mCategory;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        supportPostponeEnterTransition();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        setUpTransitions();

        tm = TrackManager.getInstance();
        mCategory = getIntent().getIntExtra(KEY_ITEM_CATEGORY, -1);
        mAlbumId = getIntent().getLongExtra(ALBUM_ID, 0);
        title = getIntent().getStringExtra(KEY_TITLE);
        loadImage();

        findViewById(R.id.details_activity_btn_close).setOnClickListener(v -> supportFinishAfterTransition());
        MaterialTextView detailsTitle = findViewById(R.id.details_activity_title);
        detailsTitle.setText(title);

        loadItems();
    }

    private void loadImage() {
        String transitionName = getIntent().getStringExtra(KEY_TRANSITION_NAME);
        String artUrl = getIntent().getStringExtra(KEY_ART_URL);
        ImageView sharedImageView = findViewById(R.id.details_activity_art);
        sharedImageView.setTransitionName(transitionName);
        if (mCategory == CATEGORY_ALBUM) {
            GlideApp.with(this)
                    .load(artUrl)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            MediaArtHelper.loadDynamicAlbumArt(sharedImageView, mAlbumId, DimensionsUtil.RoundingRadius.RADIUS_8dp, result ->
                                    sharedImageView.post(() -> {
                                        sharedImageView.setImageDrawable(result);
                                        supportStartPostponedEnterTransition();
                                    }));
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            supportStartPostponedEnterTransition();
                            return false;
                        }
                    })
                    .transform(new MultiTransformation<>(GlideConstantArtifacts.getCenterCrop(), GlideConstantArtifacts.getRadius8dp()))
                    .into(sharedImageView);
        } else {
            sharedImageView.setImageResource(R.drawable.ic_artist_art);
            supportStartPostponedEnterTransition();
        }
    }

    private void loadItems() {
        TaskRunner.executeAsync(new ItemsLoader(title, mCategory), (data) -> {
            if (data.size() > 0) {
                mList = data;
                MaterialTextView sub = findViewById(R.id.details_activity_title_sub);
                sub.setText(String.format(Locale.ENGLISH, "%s %d %s", getString(R.string.num_tracks), mList.size(), getString(R.string.tracks)));

                RecyclerView rv = findViewById(R.id.details_activity_rv);
                rv.setVisibility(View.VISIBLE);
                rv.setHasFixedSize(true);
                rv.setVerticalFadingEdgeEnabled(true);
                rv.setLayoutManager(new LinearLayoutManager(rv.getContext(), RecyclerView.VERTICAL, false));
                DetailsAdapter adapter = new DetailsAdapter(mList, this, getLayoutInflater());
                rv.setAdapter(adapter);
            }
        });
    }

    @Override
    public void onItemClick(int pos) {
        if (null != mList) {
            tm.buildDataList(mList, pos);
            playMedia();
        }
    }

    @Override
    public void onOptionsClick(int pos) {
        View view = View.inflate(this, R.layout.library_item_menu, null);
        BottomSheetDialog bottomSheetDialog = new RoundedBottomSheetDialog(view.getContext());

        view.findViewById(R.id.track_play_next)
                .setOnClickListener(v -> {
                    tm.playNext(mList.get(pos));
                    Toast.makeText(v.getContext(), getString(R.string.play_next_toast), Toast.LENGTH_SHORT).show();
                    if (bottomSheetDialog.isShowing())
                        bottomSheetDialog.dismiss();
                });

        view.findViewById(R.id.add_to_queue)
                .setOnClickListener(v -> {
                    tm.addToActiveQueue(mList.get(pos));
                    Toast.makeText(v.getContext(), getString(R.string.add_to_queue_toast), Toast.LENGTH_SHORT).show();
                    if (bottomSheetDialog.isShowing())
                        bottomSheetDialog.dismiss();
                });

        view.findViewById(R.id.song_info).setOnClickListener(v -> {
            UIHelper.buildSongInfoDialog(this, mList.get(pos));
            if (bottomSheetDialog.isShowing())
                bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();
    }

    private void setUpTransitions() {
        Fade enterFade = new Fade();
        enterFade.excludeTarget(android.R.id.statusBarBackground, true);
        enterFade.excludeTarget(android.R.id.navigationBarBackground, true);
        enterFade.excludeTarget(R.id.details_activity_rv, true);
        enterFade.setDuration(275);
        getWindow().setEnterTransition(enterFade);
    }

    @Override
    public void onMediaServiceConnected(MediaController controller) {
    }
}
