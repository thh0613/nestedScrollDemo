package com.taohuahua.nestedviewdemo;
import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewParentCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.LinkedList;
import java.util.List;

public class SwipeLoadLayout extends FrameLayout implements NestedScrollingParent, NestedScrollingChild {

  private NestedScrollingParentHelper mNestedScrollingParentHelper;
  private NestedScrollingChildHelper mNestedScrollingChildHelper;
  private final int[] mParentScrollConsumed = new int[2];
  private final int[] mParentOffsetInWindow = new int[2];
  private boolean mNestedScrollInProgress;
  private OnRefreshListener onRefreshListener;
  private OnLoadingListener onLoadingListener;
  private ViewParent mNestedScrollAcceptedParent;

  private FrameLayout mHeaderView;
  private FrameLayout mFooterView;
  private RecyclerView mRecyclerView;

  private static final int INVALID = -1;
  private static final int PULL_REFRESH = 0;
  private static final int LOAD_MORE = 1;

  // Is Refreshing
  volatile private boolean mRefreshing = false;

  // RefreshView Height
  private volatile float refreshViewHeight = 0;
  private volatile float loadingViewHeight = 0;


  private static final float overFlow = 1.0f;

  private static final float DAMPING = 0.4f;

  // Drag Action
  private int mCurrentAction = -1;
  private boolean isConfirm = false;

  public SwipeLoadLayout(@NonNull Context context) {
    super(context);
  }

  public SwipeLoadLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public SwipeLoadLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    mHeaderView = findViewById(R.id.refresh_view);
    mFooterView = findViewById(R.id.load_view);
    mRecyclerView = findViewById(R.id.recyler_view);
    initHelper();
  }

  private void initHelper() {
    mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
    mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
    setNestedScrollingEnabled(false);

    refreshViewHeight = 500;
    loadingViewHeight = 500;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (!isEnabled() || canChildScrollUp()
            || mRefreshing || mNestedScrollInProgress) {
      // Fail fast if we're not in a state where a swipe is possible
      return false;
    }

    return super.onInterceptTouchEvent(ev);
  }

  // NestedScrollingChild

  @Override
  public void setNestedScrollingEnabled(boolean enabled) {
    mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
  }

  @Override
  public boolean isNestedScrollingEnabled() {
    return mNestedScrollingChildHelper.isNestedScrollingEnabled();
  }

  @Override
  public boolean startNestedScroll(int axes) {
    boolean result = mNestedScrollingChildHelper.startNestedScroll(axes);
    if(result){
      if(mNestedScrollAcceptedParent == null){
        ViewParent parent  = this.getParent();
        View child = this;
        while (parent != null) {
          if (ViewParentCompat.onStartNestedScroll(parent, child, this, axes)){
            mNestedScrollAcceptedParent = parent;
            break;
          }
          if(parent instanceof  View){
            child = (View) parent;
          }
          parent = parent.getParent();
        }
      }
    }
    return result;
  }

  @Override
  public void stopNestedScroll() {
    mNestedScrollingChildHelper.stopNestedScroll();
    if(mNestedScrollAcceptedParent != null){
      mNestedScrollAcceptedParent = null;
    }
  }

  @Override
  public boolean hasNestedScrollingParent() {
    return mNestedScrollingChildHelper.hasNestedScrollingParent();
  }

  @Override
  public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                      int dyUnconsumed, int[] offsetInWindow) {
    return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
            dxUnconsumed, dyUnconsumed, offsetInWindow);
  }

  @Override
  public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
    return mNestedScrollingChildHelper.dispatchNestedPreScroll(
            dx, dy, consumed, offsetInWindow);
  }



  @Override
  public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
    return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
  }

  @Override
  public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
    return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
  }

  /*********************************** NestedScrollParent *************************************/


  @Override
  public boolean onNestedPreFling(View target, float velocityX,
                                  float velocityY) {
    if(isNestedScrollingEnabled()) {
      return dispatchNestedPreFling(velocityX, velocityY);
    }
    return  false;
  }

  @Override
  public boolean onNestedFling(View target, float velocityX, float velocityY,
                               boolean consumed) {
    if(isNestedScrollingEnabled()) {
      return dispatchNestedFling(velocityX, velocityY, consumed);
    }
    return  false;
  }

  @Override
  public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
    boolean result =  isEnabled()  && !mRefreshing
            && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;

    return  result;
  }

  @Override
  public void onNestedScrollAccepted(View child, View target, int axes) {
    mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
    if(isNestedScrollingEnabled()){
      startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);
      mNestedScrollInProgress = true;
    }
  }


  /**
   * With child view to processing move events
   * @param target the child view
   * @param dx move x
   * @param dy move y
   * @param consumed parent consumed move distance
   */
  @Override
  public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
    // Now let our nested parent consume the leftovers
    final int[] parentConsumed = mParentScrollConsumed;
    if(isNestedScrollingEnabled()){
      if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
        consumed[0] += parentConsumed[0];
        consumed[1] += parentConsumed[1];
        return;
      }
    }


    /**
     * when in nest-scroll, list canChildScrollUp() false,
     * maybe parent scroll can scroll up
     * */
    if(!canChildScrollUp() && isNestedScrollingEnabled()){
      if(mNestedScrollAcceptedParent != null && mNestedScrollAcceptedParent != mRecyclerView){
        ViewGroup group = (ViewGroup) mNestedScrollAcceptedParent;
        if(group.getChildCount() > 0){
          int count = group.getChildCount();
          for(int i=0; i<count; i++){
            View view  = group.getChildAt(i);
            if(view.getVisibility() != View.GONE && view.getMeasuredHeight() > 0){
              if(view.getTop() < 0){
                return;
              }else{
                break;
              }
            }
          }
        }
      }
    }



    int spinnerDy = (int) calculateDistanceY(target, dy);

    mRefreshing = false;

    if (!isConfirm) {
      if (spinnerDy < 0 && !canChildScrollUp()) {
        mCurrentAction = PULL_REFRESH;
        isConfirm = true;
      } else if (spinnerDy > 0 && !canChildScrollDown() && (!mRefreshing)) {
        mCurrentAction = LOAD_MORE;
        isConfirm = true;
      }
    }

    if (moveSpinner(-spinnerDy)) {
      if (!canChildScrollUp()
              && mRecyclerView.getTranslationY() > 0
              && dy > 0) {
        consumed[1] += dy;
      }else if (!canChildScrollDown()
              && mRecyclerView.getTranslationY() < 0
              && dy < 0){
        consumed[1] += dy;
      }else{
        consumed[1] += spinnerDy;
      }
    }
  }

  @Override
  public int getNestedScrollAxes() {
    return mNestedScrollingParentHelper.getNestedScrollAxes();
  }


  /**
   * Callback on TouchEvent.ACTION_CANCLE or TouchEvent.ACTION_UP
   * handler : refresh or loading
   * @param child : child view of SwipeLayout,RecyclerView or Scroller
   */
  @Override
  public void onStopNestedScroll(View child) {
    mNestedScrollingParentHelper.onStopNestedScroll(child);
    handlerAction();
    if(isNestedScrollingEnabled()) {
      mNestedScrollInProgress = true;
      stopNestedScroll();
    }
  }


  @Override
  public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
    if(isNestedScrollingEnabled()) {
      dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);
    }
  }





  private double calculateDistanceY(View target, int dy) {
    int viewHeight = target.getMeasuredHeight();
    double ratio = (viewHeight - Math.abs(target.getY())) / 1.0d / viewHeight * DAMPING;
    if (ratio <= 0.01d) {
      //Filter tiny scrolling action
      ratio = 0.01d;
    }
    return ratio * dy;
  }

  /**
   * Adjust the refresh or loading view according to the size of the gesture
   *
   * @param distanceY move distance of Y
   */
  private boolean moveSpinner(float distanceY) {
    if (mRefreshing) {
      return false;
    }

    if (!canChildScrollUp()  && mCurrentAction == PULL_REFRESH) {
      // Pull Refresh
      LayoutParams lp = (LayoutParams) mHeaderView.getLayoutParams();
      lp.height += distanceY;
      if (lp.height < 0) {
        lp.height = 0;
      }

      if (lp.height == 0) {
        isConfirm = false;
        mCurrentAction = INVALID;
      }
      mHeaderView.setLayoutParams(lp);
//      onRefreshListener.onPullingDown(distanceY, lp.height, refreshViewFlowHeight);
      notifyOnRefreshOffsetChangedListener(lp.height);
     // mHeaderView.setProgressRotation(lp.height / refreshViewFlowHeight);
      moveTargetView(lp.height);
      return true;
    } else if (!canChildScrollDown()  && mCurrentAction == LOAD_MORE) {
      // Load more
      LayoutParams lp = (LayoutParams) mFooterView.getLayoutParams();
      lp.height -= distanceY;
      if (lp.height < 0) {
        lp.height = 0;
      }

      if (lp.height == 0) {
        isConfirm = false;
        mCurrentAction = INVALID;
      }
      mFooterView.setLayoutParams(lp);
//      onLoadingListener.onPullingUp(distanceY, lp.height, loadingViewFlowHeight);
     // mFooterView.setProgressRotation(lp.height / loadingViewFlowHeight);
      moveTargetView(-lp.height);
      return true;
    }
    return false;
  }

  /**
   * Adjust contentView(Scroller or List) at refresh or loading time
   * @param h Height of refresh view or loading view
   */
  private void moveTargetView(float h) {
    mRecyclerView.setTranslationY(h);
  }

  /**
   * Decide on the action refresh or loadmore
   */
  private void handlerAction() {

    if (isRefreshing()) {
      return;
    }
    isConfirm = false;

    LayoutParams lp;
    if (mCurrentAction == PULL_REFRESH) {
      lp = (LayoutParams) mHeaderView.getLayoutParams();
      if (lp.height >= refreshViewHeight) {
        startRefresh(lp.height);
      } else if (lp.height > 0) {
        resetHeaderView(lp.height);
      } else {
        resetRefreshState();
      }
    }

    if (mCurrentAction == LOAD_MORE) {
      lp = (LayoutParams) mFooterView.getLayoutParams();
      if (lp.height >= loadingViewHeight) {
        startLoadmore(lp.height);
      } else if (lp.height > 0) {
        resetFootView(lp.height);
      } else {
        resetLoadmoreState();
      }
    }
  }

  /**
   * Start Refresh
   * @param headerViewHeight
   */
  private void startRefresh(int headerViewHeight) {
    mRefreshing = true;
    ValueAnimator animator = ValueAnimator.ofFloat(headerViewHeight, refreshViewHeight);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        LayoutParams lp = (LayoutParams) mHeaderView.getLayoutParams();
        lp.height = (int) ((Float) animation.getAnimatedValue()).floatValue();
        notifyOnRefreshOffsetChangedListener(lp.height);
        mHeaderView.setLayoutParams(lp);
        moveTargetView(lp.height);
      }
    });
    animator.addListener(new WXRefreshAnimatorListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        mRefreshing = false;
        if (onRefreshListener != null) {
          onRefreshListener.onRefresh();
        }
      }
    });
    animator.setDuration(300);
    animator.start();


    new Handler().postDelayed(new Runnable() {
      @Override
      public void run() {
        finishPullRefresh();
      }
    }, 1800L);
  }

  /**
   * Reset refresh state
   * @param headerViewHeight
   */
  private void resetHeaderView(int headerViewHeight) {
    ValueAnimator animator = ValueAnimator.ofFloat(headerViewHeight, 0);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        LayoutParams lp = (LayoutParams) mHeaderView.getLayoutParams();
        lp.height = (int) ((Float) animation.getAnimatedValue()).floatValue();
        notifyOnRefreshOffsetChangedListener(lp.height);
        mHeaderView.setLayoutParams(lp);
        moveTargetView(lp.height);
      }
    });
    animator.addListener(new WXRefreshAnimatorListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        resetRefreshState();

      }
    });
    animator.setDuration(300);
    animator.start();


  }

  private void resetRefreshState() {
    mRefreshing = false;
    isConfirm = false;
    mCurrentAction = -1;
  }

  /**
   * Start loadmore
   * @param headerViewHeight
   */
  private void startLoadmore(int headerViewHeight) {
    mRefreshing = true;
    ValueAnimator animator = ValueAnimator.ofFloat(headerViewHeight, loadingViewHeight);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        LayoutParams lp = (LayoutParams) mFooterView.getLayoutParams();
        lp.height = (int) ((Float) animation.getAnimatedValue()).floatValue();
        mFooterView.setLayoutParams(lp);
        moveTargetView(-lp.height);
      }
    });
    animator.addListener(new WXRefreshAnimatorListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        mRefreshing = false;
        if (onLoadingListener != null) {
          onLoadingListener.onLoading();
        }
      }
    });
    animator.setDuration(300);
    animator.start();

    new Handler().postDelayed(new Runnable() {
      @Override
      public void run() {
        finishPullLoad();
      }
    }, 1800L);
  }

  /**
   * Reset loadmore state
   * @param headerViewHeight
   */
  private void resetFootView(int headerViewHeight) {
    ValueAnimator animator = ValueAnimator.ofFloat(headerViewHeight, 0);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        LayoutParams lp = (LayoutParams) mFooterView.getLayoutParams();
        lp.height = (int) ((Float) animation.getAnimatedValue()).floatValue();
        mFooterView.setLayoutParams(lp);
        moveTargetView(-lp.height);
      }
    });
    animator.addListener(new WXRefreshAnimatorListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        resetLoadmoreState();

      }
    });
    animator.setDuration(300);
    animator.start();
  }

  private void resetLoadmoreState() {
    mRefreshing = false;
    isConfirm = false;
    mCurrentAction = -1;
  }

  /**
   * Whether child view can scroll up
   * @return
   */
  public boolean canChildScrollUp() {
    if (mRecyclerView == null) {
      return false;
    }
    return ViewCompat.canScrollVertically(mRecyclerView, -1);
  }

  /**
   * Whether child view can scroll down
   * @return
   */
  public boolean canChildScrollDown() {
    if (mRecyclerView == null) {
      return false;
    }
    return ViewCompat.canScrollVertically(mRecyclerView, 1);
  }

  public void setOnLoadingListener(OnLoadingListener onLoadingListener) {
    this.onLoadingListener = onLoadingListener;
  }

  public void setOnRefreshListener(OnRefreshListener onRefreshListener) {
    this.onRefreshListener = onRefreshListener;
  }

  @SuppressWarnings("unused")
  public void addOnRefreshOffsetChangedListener(@Nullable OnRefreshOffsetChangedListener listener) {
    if(listener != null && !mRefreshOffsetChangedListeners.contains(listener)) {
      mRefreshOffsetChangedListeners.add(listener);
    }
  }

  @SuppressWarnings("unused")
  public boolean removeOnRefreshOffsetChangedListener(@Nullable OnRefreshOffsetChangedListener listener) {
    if(listener != null) {
      return mRefreshOffsetChangedListeners.remove(listener);
    }
    return false;
  }

  private void notifyOnRefreshOffsetChangedListener(int verticalOffset) {
    int size = mRefreshOffsetChangedListeners.size();
    OnRefreshOffsetChangedListener listener;
    for (int i=0; i<size; i++) {
      if(i >= mRefreshOffsetChangedListeners.size()){
        break;
      }
      listener = mRefreshOffsetChangedListeners.get(i);

      if (listener != null) {
        listener.onOffsetChanged(verticalOffset);
      }
    }
  }

  /**
   * Callback on refresh finish
   */
  public void finishPullRefresh() {
    if (mCurrentAction == PULL_REFRESH) {
      resetHeaderView(mHeaderView == null ? 0 : mHeaderView.getMeasuredHeight());
    }
  }

  /**
   * Callback on loadmore finish
   */
  public void finishPullLoad() {
    if (mCurrentAction == LOAD_MORE) {
      resetFootView(mFooterView == null ? 0 : mFooterView.getMeasuredHeight());
    }
  }

  public boolean isRefreshing() {
    return mRefreshing;
  }

  /**
   * On refresh Callback, call on start refresh
   */
  public interface OnRefreshListener {

    void onRefresh();

    void onPullingDown(float dy, int pullOutDistance, float viewHeight);
  }

  /**
   * On loadmore Callback, call on start loadmore
   */
  public interface OnLoadingListener {

    void onLoading();
    void onPullingUp(float dy, int pullOutDistance, float viewHeight);
  }

  private final List<OnRefreshOffsetChangedListener> mRefreshOffsetChangedListeners = new LinkedList<>();

  public interface OnRefreshOffsetChangedListener {
    void onOffsetChanged(int verticalOffset);
  }


  static class WXRefreshAnimatorListener implements Animator.AnimatorListener {

    @Override
    public void onAnimationStart(Animator animation) {
    }

    @Override
    public void onAnimationEnd(Animator animation) {
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }
  }

}
