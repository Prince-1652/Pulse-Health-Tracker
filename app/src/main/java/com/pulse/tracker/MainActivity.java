package com.pulse.tracker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.card.MaterialCardView;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.ui.history.HistoryFragment;
import com.pulse.tracker.ui.home.TodayFragment;
import com.pulse.tracker.ui.onboarding.OnboardingActivity;
import com.pulse.tracker.ui.profile.ProfileFragment;
import com.pulse.tracker.ui.water.WaterFragment;

/**
 * Main application shell hosting Steps, Water, History, and Profile destinations.
 * Features an icon-only split bottom navigation dock:
 * 3 primary tabs (Steps, Water, History) grouped on the left with 60fps sliding white pill glide,
 * and a separated circular Profile tab on the right clicked independently.
 */
public class MainActivity extends AppCompatActivity {

    public static final String TAG_STEPS = "tag_today";
    public static final String TAG_TODAY = "tag_today";
    public static final String TAG_WATER = "tag_water";
    public static final String TAG_HISTORY = "tag_history";
    public static final String TAG_PROFILE = "tag_profile";
    public static final String KEY_ACTIVE_TAG = "key_active_tag";
    public static final String KEY_ACTIVE_POSITION = "key_active_position";

    public static final int POSITION_STEPS = 0;
    public static final int POSITION_TODAY = 0;
    public static final int POSITION_WATER = 1;
    public static final int POSITION_HISTORY = 2;
    public static final int POSITION_PROFILE = 3;

    private static final boolean IS_TEST = "robolectric".equals(Build.FINGERPRINT)
            || "robolectric".equals(Build.HARDWARE)
            || (Build.FINGERPRINT != null && Build.FINGERPRINT.contains("robolectric"));

    private ViewPager2 viewPager;
    private MainPagerAdapter pagerAdapter;

    private MaterialCardView navPillContainer;
    private MaterialCardView navProfileCard;
    private View navSlidingPill;
    private LinearLayout navTabsRow;
    private float activeTabWidth = 0f;

    private ValueAnimator morphAnimator;

    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();
    private int colorInactive;
    private int colorActive;

    private View tabSteps;
    private View tabToday;
    private View tabWater;
    private View tabHistory;
    private View tabProfile;

    private View indicatorSteps;
    private View indicatorToday;
    private View indicatorWater;
    private View indicatorHistory;
    private View indicatorProfile;

    private ImageView iconSteps;
    private ImageView iconToday;
    private ImageView iconWater;
    private ImageView iconHistory;
    private ImageView iconProfile;

    private TextView labelSteps;
    private TextView labelToday;
    private TextView labelWater;
    private TextView labelHistory;
    private TextView labelProfile;

    private int currentPosition = POSITION_STEPS;
    private boolean isUserDragging = false;

    /**
     * Physical damped harmonic spring oscillator with gentle organic overshoot (~5.8%)
     * and smooth settling, matching Apple fluid design specifications.
     */
    public static class SpringInterpolator implements Interpolator {
        private final float damping;
        private final float frequency;

        public SpringInterpolator(float damping, float frequency) {
            this.damping = damping;
            this.frequency = frequency;
        }

        @Override
        public float getInterpolation(float input) {
            if (input <= 0f) return 0f;
            if (input >= 1f) return 1f;
            double raw = 1.0 - Math.exp(-damping * input) * Math.cos(frequency * input);
            double endVal = 1.0 - Math.exp(-damping) * Math.cos(frequency);
            return (float) (raw / endVal);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SettingsRepository settingsRepository = SettingsRepository.getInstance(this);
        if (!settingsRepository.hasCompletedOnboarding()) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        setContentView(R.layout.activity_main);

        applyStatusBarVisibility();

        bindViews();
        colorInactive = ContextCompat.getColor(this, R.color.pulse_text_secondary);
        colorActive = ContextCompat.getColor(this, R.color.pulse_on_nav_active);
        setupWindowInsets();
        setupViewPager(savedInstanceState);
        setupNavigationListeners();

        if (settingsRepository.isTrackingEnabled() && com.pulse.tracker.util.PermissionUtils.hasActivityRecognitionPermission(this)) {
            Intent serviceIntent = new Intent(this, com.pulse.tracker.service.StepTrackingService.class);
            serviceIntent.setAction(com.pulse.tracker.service.StepTrackingService.ACTION_START);
            try {
                if (settingsRepository.isNotificationEnabled()) {
                    ContextCompat.startForegroundService(this, serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } catch (Exception e) {
                startService(serviceIntent);
            }
        }
    }

    private void bindViews() {
        viewPager = findViewById(R.id.view_pager);
        navPillContainer = findViewById(R.id.nav_pill_container);
        navProfileCard = findViewById(R.id.nav_profile_card);
        navSlidingPill = findViewById(R.id.nav_sliding_pill);
        navTabsRow = findViewById(R.id.nav_tabs_row);

        tabToday = findViewById(R.id.nav_tab_today);
        tabSteps = tabToday;
        tabWater = findViewById(R.id.nav_tab_water);
        tabHistory = findViewById(R.id.nav_tab_history);
        tabProfile = findViewById(R.id.nav_tab_profile);

        indicatorToday = findViewById(R.id.indicator_tab_today);
        indicatorSteps = indicatorToday;
        indicatorWater = findViewById(R.id.indicator_tab_water);
        indicatorHistory = findViewById(R.id.indicator_tab_history);
        indicatorProfile = findViewById(R.id.indicator_tab_profile);

        iconToday = findViewById(R.id.icon_tab_today);
        iconSteps = iconToday;
        iconWater = findViewById(R.id.icon_tab_water);
        iconHistory = findViewById(R.id.icon_tab_history);
        iconProfile = findViewById(R.id.icon_tab_profile);

        labelToday = findViewById(R.id.label_tab_today);
        labelSteps = labelToday;
        labelWater = findViewById(R.id.label_tab_water);
        labelHistory = findViewById(R.id.label_tab_history);
        labelProfile = findViewById(R.id.label_tab_profile);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_main), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

            // Root main top padding = systemBars.top so content starts cleanly below visible status bar
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

            int imeBottom = ime.bottom;
            boolean isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime()) || imeBottom > 0;

            if (isImeVisible) {
                // Hide floating bottom dock when keyboard is visible to avoid overlapping inputs
                if (navPillContainer != null) {
                    navPillContainer.setVisibility(View.GONE);
                }
                if (navProfileCard != null) {
                    navProfileCard.setVisibility(View.GONE);
                }
                if (viewPager != null) {
                    viewPager.setPadding(0, 0, 0, imeBottom);
                }
            } else {
                // Position floating navigation cards cleanly 16dp above system gesture bar/insets
                int margin16 = getResources().getDimensionPixelSize(R.dimen.space_16);
                int targetMargin = systemBars.bottom + margin16;

                if (navPillContainer != null) {
                    navPillContainer.setVisibility(View.VISIBLE);
                    ViewGroup.MarginLayoutParams pillParams = (ViewGroup.MarginLayoutParams) navPillContainer.getLayoutParams();
                    pillParams.bottomMargin = targetMargin;
                    navPillContainer.setLayoutParams(pillParams);
                }
                if (navProfileCard != null) {
                    navProfileCard.setVisibility(View.VISIBLE);
                    ViewGroup.MarginLayoutParams profileParams = (ViewGroup.MarginLayoutParams) navProfileCard.getLayoutParams();
                    profileParams.bottomMargin = targetMargin;
                    navProfileCard.setLayoutParams(profileParams);
                }
                if (viewPager != null) {
                    viewPager.setPadding(0, 0, 0, 0);
                }
            }

            return windowInsets;
        });
    }

    /**
     * Controls whether ViewPager2 horizontal swipe gesture is enabled.
     * Used by child sliders, measuring tapes, and text inputs to prevent accidental fragment swiping.
     */
    public void setSwipeEnabled(boolean enabled) {
        if (viewPager != null) {
            viewPager.setUserInputEnabled(enabled);
        }
    }

    private void setupViewPager(Bundle savedInstanceState) {
        pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(3);

        // Smooth PageTransformer with subtle scale & alpha transition (zero GC allocations)
        viewPager.setPageTransformer((page, position) -> {
            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 1) {
                float scaleFactor = Math.max(0.95f, 1 - Math.abs(position) * 0.05f);
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
                float alphaFactor = Math.max(0.6f, 1 - Math.abs(position) * 0.4f);
                page.setAlpha(alphaFactor);
            } else {
                page.setAlpha(0f);
            }
        });

        // Restrict horizontal swiping: allow swipe between Steps (0), Water (1), History (2).
        // Disallow swiping from History (2) into Profile (3), and disallow swiping when on Profile (3).
        View child = viewPager.getChildAt(0);
        if (child instanceof RecyclerView) {
            ((RecyclerView) child).addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                private float startX = 0f;
                private float startY = 0f;

                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                    if (currentPosition == POSITION_PROFILE) {
                        return true; // Disallow swipe completely on profile
                    }
                    if (currentPosition == POSITION_HISTORY) {
                        if (e.getAction() == MotionEvent.ACTION_DOWN) {
                            startX = e.getX();
                            startY = e.getY();
                        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                            float dx = e.getX() - startX;
                            float dy = e.getY() - startY;
                            // Finger dragging left (dx < -15) tries to swipe to Profile -> intercept & block
                            if (Math.abs(dx) > Math.abs(dy) && dx < -15f) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        }

        int initialPos = POSITION_STEPS;
        Intent launchIntent = getIntent();
        if (launchIntent != null && launchIntent.hasExtra(KEY_ACTIVE_POSITION)) {
            initialPos = launchIntent.getIntExtra(KEY_ACTIVE_POSITION, POSITION_STEPS);
        } else if (launchIntent != null && launchIntent.hasExtra(KEY_ACTIVE_TAG)) {
            String tag = launchIntent.getStringExtra(KEY_ACTIVE_TAG);
            if (TAG_WATER.equals(tag)) initialPos = POSITION_WATER;
            else if (TAG_HISTORY.equals(tag)) initialPos = POSITION_HISTORY;
            else if (TAG_PROFILE.equals(tag)) initialPos = POSITION_PROFILE;
            else initialPos = POSITION_STEPS;
        } else if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_ACTIVE_POSITION)) {
                initialPos = savedInstanceState.getInt(KEY_ACTIVE_POSITION, POSITION_STEPS);
            } else if (savedInstanceState.containsKey(KEY_ACTIVE_TAG)) {
                String tag = savedInstanceState.getString(KEY_ACTIVE_TAG, TAG_STEPS);
                if (TAG_WATER.equals(tag)) initialPos = POSITION_WATER;
                else if (TAG_HISTORY.equals(tag)) initialPos = POSITION_HISTORY;
                else if (TAG_PROFILE.equals(tag)) initialPos = POSITION_PROFILE;
                else initialPos = POSITION_STEPS;
            }
        }

        currentPosition = initialPos;
        viewPager.setCurrentItem(initialPos, false);
        updateNavState(initialPos, false);
        setupSlidingPill();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                if (isUserDragging) {
                    if (position < 2) {
                        float continuousPos = position + positionOffset;
                        updatePillContinuousPosition(continuousPos);
                        applyColorCrossfade(continuousPos);
                    } else if (position == 2) {
                        updatePillContinuousPosition(2f);
                        applyColorCrossfade(2f);
                    }
                }
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                updateNavState(position, false);
                animateFragmentEntrance(position);
                if (position == POSITION_STEPS) {
                    Fragment frag = getFragmentForPosition(POSITION_STEPS);
                    if (frag instanceof TodayFragment) {
                        ((TodayFragment) frag).refreshGreeting();
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    isUserDragging = true;
                    if (morphAnimator != null && morphAnimator.isRunning()) {
                        morphAnimator.cancel();
                    }
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if (isUserDragging) {
                        isUserDragging = false;
                        updateNavState(currentPosition, false);
                    }
                }
            }
        });
    }

    private void setupSlidingPill() {
        if (navTabsRow == null || navSlidingPill == null) return;

        navTabsRow.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int totalWidth = right - left;
            if (totalWidth > 0 && totalWidth != (oldRight - oldLeft)) {
                calculateTabDimensions(totalWidth);
                updateNavState(currentPosition, false);
            }
        });

        navTabsRow.post(() -> {
            int totalWidth = navTabsRow.getWidth();
            if (totalWidth > 0) {
                calculateTabDimensions(totalWidth);
                updateNavState(currentPosition, false);
            }
        });
    }

    private void calculateTabDimensions(float totalWidth) {
        if (totalWidth <= 0f) return;
        activeTabWidth = totalWidth / 3f;
    }

    private void updatePillContinuousPosition(float continuousPos) {
        if (navSlidingPill == null || activeTabWidth <= 0f) return;
        float clampedPos = Math.max(0f, Math.min(2f, continuousPos));
        float pillX = clampedPos * activeTabWidth;
        navSlidingPill.setTranslationX(pillX);

        int targetWidth = Math.round(activeTabWidth);
        ViewGroup.LayoutParams lp = navSlidingPill.getLayoutParams();
        if (lp != null && lp.width != targetWidth && targetWidth > 0) {
            lp.width = targetWidth;
            navSlidingPill.setLayoutParams(lp);
        }
    }

    /**
     * Continuously interpolates icon tints for the 3 left tabs using ArgbEvaluator
     * with smoothstep Hermite curve. Pure GPU color transform (zero layout requests).
     */
    private void applyColorCrossfade(float continuousPos) {
        for (int i = 0; i < 3; i++) {
            float dist = Math.abs(continuousPos - i);
            float coverage = Math.max(0f, 1f - dist);
            float smoothCoverage = coverage * coverage * (3f - 2f * coverage);
            int blendedColor = (Integer) argbEvaluator.evaluate(smoothCoverage, colorInactive, colorActive);

            ImageView icon = getTabIcon(i);
            if (icon != null) {
                icon.setImageTintList(ColorStateList.valueOf(blendedColor));
            }
        }
        if (iconProfile != null) {
            iconProfile.setImageTintList(ColorStateList.valueOf(colorInactive));
        }
    }

    private void setupNavigationListeners() {
        if (tabSteps != null) tabSteps.setOnClickListener(v -> selectTab(POSITION_STEPS, true));
        if (tabWater != null) tabWater.setOnClickListener(v -> selectTab(POSITION_WATER, true));
        if (tabHistory != null) tabHistory.setOnClickListener(v -> selectTab(POSITION_HISTORY, true));
        if (tabProfile != null) tabProfile.setOnClickListener(v -> selectTab(POSITION_PROFILE, true));
    }

    /**
     * Programmatically select tab destination with fluid morphing.
     */
    public void selectTab(int position, boolean smoothScroll) {
        if (viewPager == null) return;
        if (currentPosition == position && !smoothScroll) return;

        currentPosition = position;
        isUserDragging = false;

        if (IS_TEST || !smoothScroll || navTabsRow == null || !navTabsRow.isLaidOut()) {
            updateNavState(position, false);
            viewPager.setCurrentItem(position, false);
        } else {
            updateNavState(position, true);
            viewPager.setCurrentItem(position, true);
        }
    }

    /**
     * Updates active navigation state between the left 3 tabs and the separated Profile button.
     */
    public void updateNavState(int targetPosition, boolean animate) {
        currentPosition = targetPosition;

        if (targetPosition == POSITION_PROFILE) {
            // Profile active on right
            if (indicatorProfile != null) {
                indicatorProfile.setVisibility(View.VISIBLE);
                if (animate && !IS_TEST) {
                    indicatorProfile.setAlpha(0f);
                    indicatorProfile.animate().alpha(1f).setDuration(220).start();
                } else {
                    indicatorProfile.setAlpha(1f);
                }
            }
            if (iconProfile != null) {
                iconProfile.setImageTintList(ColorStateList.valueOf(colorActive));
            }

            // Left dock inactive
            if (navSlidingPill != null) {
                if (animate && !IS_TEST) {
                    navSlidingPill.animate().alpha(0f).setDuration(180)
                            .withEndAction(() -> navSlidingPill.setVisibility(View.INVISIBLE)).start();
                } else {
                    navSlidingPill.setVisibility(View.INVISIBLE);
                    navSlidingPill.setAlpha(0f);
                }
            }
            if (indicatorToday != null) indicatorToday.setVisibility(View.INVISIBLE);
            if (indicatorWater != null) indicatorWater.setVisibility(View.INVISIBLE);
            if (indicatorHistory != null) indicatorHistory.setVisibility(View.INVISIBLE);

            if (iconToday != null) iconToday.setImageTintList(ColorStateList.valueOf(colorInactive));
            if (iconWater != null) iconWater.setImageTintList(ColorStateList.valueOf(colorInactive));
            if (iconHistory != null) iconHistory.setImageTintList(ColorStateList.valueOf(colorInactive));

        } else {
            // One of Steps, Water, History active on left
            if (indicatorProfile != null) {
                if (animate && !IS_TEST) {
                    indicatorProfile.animate().alpha(0f).setDuration(180)
                            .withEndAction(() -> indicatorProfile.setVisibility(View.INVISIBLE)).start();
                } else {
                    indicatorProfile.setVisibility(View.INVISIBLE);
                    indicatorProfile.setAlpha(0f);
                }
            }
            if (iconProfile != null) {
                iconProfile.setImageTintList(ColorStateList.valueOf(colorInactive));
            }

            if (indicatorToday != null) {
                indicatorToday.setVisibility(targetPosition == POSITION_STEPS ? View.VISIBLE : View.INVISIBLE);
            }
            if (indicatorWater != null) {
                indicatorWater.setVisibility(targetPosition == POSITION_WATER ? View.VISIBLE : View.INVISIBLE);
            }
            if (indicatorHistory != null) {
                indicatorHistory.setVisibility(targetPosition == POSITION_HISTORY ? View.VISIBLE : View.INVISIBLE);
            }

            if (navSlidingPill != null) {
                navSlidingPill.setVisibility(View.VISIBLE);
                int targetWidth = Math.round(activeTabWidth);
                ViewGroup.LayoutParams lp = navSlidingPill.getLayoutParams();
                if (lp != null && lp.width != targetWidth && targetWidth > 0) {
                    lp.width = targetWidth;
                    navSlidingPill.setLayoutParams(lp);
                }

                float targetX = targetPosition * activeTabWidth;
                if (animate && !IS_TEST) {
                    if (navSlidingPill.getAlpha() < 0.9f) {
                        navSlidingPill.setTranslationX(targetX);
                        navSlidingPill.animate().alpha(1f).setDuration(220).start();
                    } else {
                        animatePillTranslation(navSlidingPill.getTranslationX(), targetX, targetPosition);
                    }
                } else {
                    navSlidingPill.setAlpha(1f);
                    navSlidingPill.setTranslationX(targetX);
                }
            }

            applyColorCrossfade(targetPosition);
        }
    }

    private void animatePillTranslation(float startX, float targetX, int targetPos) {
        if (morphAnimator != null && morphAnimator.isRunning()) {
            morphAnimator.cancel();
        }
        final SpringInterpolator springInterpolator = new SpringInterpolator(5.5f, 5.2f);
        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(280);
        morphAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            float springF = springInterpolator.getInterpolation(fraction);
            float currentX = startX + (targetX - startX) * springF;
            navSlidingPill.setTranslationX(currentX);

            if (activeTabWidth > 0f) {
                float pos = Math.max(0f, Math.min(2f, currentX / activeTabWidth));
                applyColorCrossfade(pos);
            }
        });
        morphAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                navSlidingPill.setTranslationX(targetX);
                applyColorCrossfade(targetPos);
            }
        });
        morphAnimator.start();
    }

    /**
     * Public navigation helper allowing fragments (e.g. Today top header)
     * to switch directly to the Profile tab.
     */
    public void switchToProfileTab() {
        selectTab(POSITION_PROFILE, true);
    }

    /**
     * Subtle entrance settle on the newly active fragment's view hierarchy.
     */
    private void animateFragmentEntrance(int position) {
        Fragment fragment = getFragmentForPosition(position);
        if (fragment != null && fragment.getView() != null) {
            View view = fragment.getView();
            view.animate().cancel();
            view.setScaleX(0.985f);
            view.setScaleY(0.985f);
            view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(280)
                    .setInterpolator(new OvershootInterpolator(1.8f))
                    .start();
        }
    }

    public View getTabContainerForPosition(int position) {
        switch (position) {
            case POSITION_WATER:
                return tabWater;
            case POSITION_HISTORY:
                return tabHistory;
            case POSITION_PROFILE:
                return tabProfile;
            case POSITION_STEPS:
            default:
                return tabSteps;
        }
    }

    public View getTabIndicator(int position) {
        switch (position) {
            case POSITION_WATER:
                return indicatorWater;
            case POSITION_HISTORY:
                return indicatorHistory;
            case POSITION_PROFILE:
                return indicatorProfile;
            case POSITION_STEPS:
            default:
                return indicatorSteps;
        }
    }

    public ImageView getTabIcon(int position) {
        switch (position) {
            case POSITION_WATER:
                return iconWater;
            case POSITION_HISTORY:
                return iconHistory;
            case POSITION_PROFILE:
                return iconProfile;
            case POSITION_STEPS:
            default:
                return iconSteps;
        }
    }

    public TextView getTabLabel(int position) {
        switch (position) {
            case POSITION_WATER:
                return labelWater;
            case POSITION_HISTORY:
                return labelHistory;
            case POSITION_PROFILE:
                return labelProfile;
            case POSITION_STEPS:
            default:
                return labelSteps;
        }
    }

    public void updateTabVisuals(int position) {
        updateNavState(position, false);
    }

    /**
     * Resolves the fragment instance attached by ViewPager2 for a given position.
     */
    @Nullable
    public Fragment getFragmentForPosition(int position) {
        return getSupportFragmentManager().findFragmentByTag("f" + position);
    }

    @Nullable
    public Fragment getFragmentByTagCompat(String tag) {
        if (TAG_STEPS.equals(tag) || TAG_TODAY.equals(tag)) return getFragmentForPosition(POSITION_STEPS);
        if (TAG_WATER.equals(tag)) return getFragmentForPosition(POSITION_WATER);
        if (TAG_HISTORY.equals(tag)) return getFragmentForPosition(POSITION_HISTORY);
        if (TAG_PROFILE.equals(tag)) return getFragmentForPosition(POSITION_PROFILE);
        return getSupportFragmentManager().findFragmentByTag(tag);
    }

    @VisibleForTesting
    public ViewPager2 getViewPager() {
        return viewPager;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            if (intent.hasExtra(KEY_ACTIVE_POSITION)) {
                int targetPos = intent.getIntExtra(KEY_ACTIVE_POSITION, POSITION_STEPS);
                selectTab(targetPos, true);
            } else if (intent.hasExtra(KEY_ACTIVE_TAG)) {
                String tag = intent.getStringExtra(KEY_ACTIVE_TAG);
                if (TAG_WATER.equals(tag)) selectTab(POSITION_WATER, true);
                else if (TAG_HISTORY.equals(tag)) selectTab(POSITION_HISTORY, true);
                else if (TAG_PROFILE.equals(tag)) selectTab(POSITION_PROFILE, true);
                else selectTab(POSITION_STEPS, true);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyStatusBarVisibility();
        SettingsRepository settings = SettingsRepository.getInstance(this);
        if (settings.isTrackingEnabled() && !settings.isTrackingPaused() && com.pulse.tracker.util.PermissionUtils.hasActivityRecognitionPermission(this)) {
            try {
                Intent serviceIntent = new Intent(this, com.pulse.tracker.service.StepTrackingService.class);
                serviceIntent.setAction(com.pulse.tracker.service.StepTrackingService.ACTION_START);
                if (settings.isNotificationEnabled()) {
                    ContextCompat.startForegroundService(this, serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "Failed to start StepTrackingService: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (morphAnimator != null) {
            morphAnimator.cancel();
            morphAnimator = null;
        }
        super.onDestroy();
    }

    private void applyStatusBarVisibility() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars());
            controller.setAppearanceLightStatusBars(false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTIVE_POSITION, currentPosition);
        String tag = TAG_STEPS;
        if (currentPosition == POSITION_WATER) {
            tag = TAG_WATER;
        } else if (currentPosition == POSITION_HISTORY) {
            tag = TAG_HISTORY;
        } else if (currentPosition == POSITION_PROFILE) {
            tag = TAG_PROFILE;
        }
        outState.putString(KEY_ACTIVE_TAG, tag);
    }

    /**
     * Adapter for ViewPager2 page management.
     */
    public static class MainPagerAdapter extends FragmentStateAdapter {

        public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case POSITION_WATER:
                    return WaterFragment.newInstance();
                case POSITION_HISTORY:
                    return HistoryFragment.newInstance();
                case POSITION_PROFILE:
                    return ProfileFragment.newInstance();
                case POSITION_STEPS:
                default:
                    return TodayFragment.newInstance();
            }
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }
}
