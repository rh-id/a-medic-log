package m.co.rh.id.a_medic_log.app.ui.component.report;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.AdherenceReport;

/**
 * Dependency-free vertical bar chart, one bar per day.
 * The bottom segment is the taken count, the top segment the missed count,
 * both scaled to the maximum daily total. Zero-data days draw a thin
 * baseline tick instead of a bar.
 */
public class AdherenceBarChartView extends View {
    private static final float BAR_WIDTH_RATIO = 0.7f;
    private static final float LABEL_TEXT_SIZE_SP = 10f;
    private static final float MIN_BAR_HEIGHT_RATIO = 0.01f;
    private static final float MIN_BAR_HEIGHT_PX = 2f;

    private final Paint mTakenPaint;
    private final Paint mMissedPaint;
    private final Paint mLabelPaint;
    private final Paint mTickPaint;
    private List<AdherenceReport.DailyCount> mDailyCounts;

    public AdherenceBarChartView(Context context) {
        this(context, null);
    }

    public AdherenceBarChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdherenceBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTakenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTakenPaint.setStyle(Paint.Style.FILL);
        mTakenPaint.setColor(ContextCompat.getColor(context, R.color.adherence_taken));
        mMissedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMissedPaint.setStyle(Paint.Style.FILL);
        mMissedPaint.setColor(ContextCompat.getColor(context, R.color.adherence_missed));
        mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLabelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                LABEL_TEXT_SIZE_SP, getResources().getDisplayMetrics()));
        mLabelPaint.setTextAlign(Paint.Align.LEFT);
        int primaryTextColor = resolvePrimaryTextColor(context);
        mLabelPaint.setColor(primaryTextColor);
        mTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTickPaint.setStyle(Paint.Style.FILL);
        mTickPaint.setColor(primaryTextColor);
        mTickPaint.setAlpha(120);
    }

    private static int resolvePrimaryTextColor(Context context) {
        TypedArray typedArray = context.obtainStyledAttributes(
                new int[]{android.R.attr.textColorPrimary});
        int color = typedArray.getColor(0, Color.GRAY);
        typedArray.recycle();
        return color;
    }

    public void setData(List<AdherenceReport.DailyCount> dailyCounts) {
        mDailyCounts = dailyCounts;
        setContentDescription(buildContentDescription());
        invalidate();
    }

    private String buildContentDescription() {
        int taken = 0;
        int missed = 0;
        int days = 0;
        if (mDailyCounts != null) {
            days = mDailyCounts.size();
            for (AdherenceReport.DailyCount dailyCount : mDailyCounts) {
                taken += dailyCount.taken;
                missed += dailyCount.missed;
            }
        }
        return getContext().getString(R.string.adherence_chart_summary, taken, missed, days);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDailyCounts == null || mDailyCounts.isEmpty()) {
            return;
        }
        int count = mDailyCounts.size();
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        float labelHeight = mLabelPaint.getFontSpacing();
        float chartTop = paddingTop;
        float chartBottom = getHeight() - paddingBottom - labelHeight;
        float chartHeight = chartBottom - chartTop;
        if (chartHeight <= 0) {
            return;
        }
        float drawableWidth = getWidth() - paddingLeft - paddingRight;
        if (drawableWidth <= 0) {
            return;
        }
        float slotWidth = drawableWidth / count;
        float barWidth = Math.max(1f, slotWidth * BAR_WIDTH_RATIO);
        int maxTotal = 1;
        for (AdherenceReport.DailyCount dailyCount : mDailyCounts) {
            maxTotal = Math.max(maxTotal, dailyCount.taken + dailyCount.missed);
        }
        float minBarHeight = Math.max(MIN_BAR_HEIGHT_PX, chartHeight * MIN_BAR_HEIGHT_RATIO);
        boolean sparseLabels = count > 15;
        float textBaseline = chartBottom + labelHeight - mLabelPaint.descent();
        for (int i = 0; i < count; i++) {
            AdherenceReport.DailyCount dailyCount = mDailyCounts.get(i);
            float left = paddingLeft + i * slotWidth + (slotWidth - barWidth) / 2f;
            int taken = dailyCount.taken;
            int missed = dailyCount.missed;
            if (taken + missed == 0) {
                // zero-data day: thin baseline tick
                canvas.drawRect(left, chartBottom - minBarHeight,
                        left + barWidth, chartBottom, mTickPaint);
                continue;
            }
            float takenHeight = taken * chartHeight / maxTotal;
            float missedHeight = missed * chartHeight / maxTotal;
            // keep nonzero segments visible even for tiny values
            if (taken > 0) {
                takenHeight = Math.max(takenHeight, minBarHeight);
            }
            if (missed > 0) {
                missedHeight = Math.max(missedHeight, minBarHeight);
            }
            // clamp so the stacked segments never overflow the chart area
            float overflow = takenHeight + missedHeight - chartHeight;
            if (overflow > 0) {
                if (missedHeight >= takenHeight) {
                    missedHeight = Math.max(0f, missedHeight - overflow);
                } else {
                    takenHeight = Math.max(0f, takenHeight - overflow);
                }
            }
            // taken at the bottom, missed stacked on top
            canvas.drawRect(left, chartBottom - takenHeight,
                    left + barWidth, chartBottom, mTakenPaint);
            if (missedHeight > 0) {
                canvas.drawRect(left, chartBottom - takenHeight - missedHeight,
                        left + barWidth, chartBottom - takenHeight, mMissedPaint);
            }
            boolean drawLabel = !sparseLabels || i % 5 == 0 || i == count - 1;
            if (drawLabel) {
                String label = String.valueOf(dayOfMonth(dailyCount.date));
                float textWidth = mLabelPaint.measureText(label);
                canvas.drawText(label, left + (barWidth - textWidth) / 2f,
                        textBaseline, mLabelPaint);
            }
        }
    }

    private static int dayOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_MONTH);
    }
}
