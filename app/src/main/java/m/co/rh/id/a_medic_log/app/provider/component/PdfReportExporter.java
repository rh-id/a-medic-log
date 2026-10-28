package m.co.rh.id.a_medic_log.app.provider.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.AdherenceReport;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;

/**
 * Renders a single profile's medication report into a PDF file using the
 * framework {@link PdfDocument} (no external dependency):
 * a header block, a medications table, the 7-day and 30-day adherence
 * summaries and an intake-history appendix.
 * <p>
 * Rendering runs in two passes: pass 1 measures every content element (no
 * document is created) and paginates them, pass 2 creates each page, draws
 * the elements belonging to it, stamps the "Page x of y" footer before the
 * page is finished, and finally writes the document to the output file.
 * This is required because PdfDocument allows only one active page and no
 * drawing after {@link PdfDocument#finishPage}, while the total page count
 * is already needed for the footer of the very first page.
 */
public class PdfReportExporter {
    // A4 portrait in PostScript points (1/72 inch)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 40f;
    private static final float FOOTER_HEIGHT = 24f;
    private static final float CONTENT_LEFT = MARGIN;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float CONTENT_TOP = MARGIN;
    private static final float CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT;

    private static final float TEXT_SIZE_TITLE = 16f;
    private static final float TEXT_SIZE_PROFILE = 13f;
    private static final float TEXT_SIZE_SECTION = 12f;
    private static final float TEXT_SIZE_BODY = 10f;
    private static final float TEXT_SIZE_TABLE = 9f;
    private static final float TEXT_SIZE_SMALL = 8f;

    private static final float LINE_SPACING_MULT = 1.15f;
    private static final float CELL_VERTICAL_PADDING = 3f;
    private static final float SPACING_SECTION = 12f;
    private static final float SPACING_SUB_BLOCK = 4f;

    // hairline rules: thin for data rows, thicker below header rows
    private static final float RULE_WIDTH_ROW = 0.5f;
    private static final float RULE_WIDTH_HEADER = 1.5f;

    private static final String DATE_FORMAT = "dd MMM yyyy";
    private static final String TIME_FORMAT = "HH:mm";
    private static final String GENERATED_AT_FORMAT = "dd MMM yyyy HH:mm";
    private static final String PERIOD_SEPARATOR = " \u2013 ";
    private static final String ADHERENCE_LINE_SEPARATOR = " \u2014 ";

    private final Context mAppContext;
    private final TextPaint mTitlePaint;
    private final TextPaint mProfilePaint;
    private final TextPaint mSectionPaint;
    private final TextPaint mSubHeaderPaint;
    private final TextPaint mBodyPaint;
    private final TextPaint mTableHeaderPaint;
    private final TextPaint mTableCellPaint;
    private final TextPaint mSmallPaint;
    private final Paint mRowRulePaint;
    private final Paint mHeaderRulePaint;
    private final Paint mFooterPaint;

    public PdfReportExporter(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mTitlePaint = createTextPaint(TEXT_SIZE_TITLE, true);
        mProfilePaint = createTextPaint(TEXT_SIZE_PROFILE, true);
        mSectionPaint = createTextPaint(TEXT_SIZE_SECTION, true);
        mSubHeaderPaint = createTextPaint(TEXT_SIZE_BODY, true);
        mBodyPaint = createTextPaint(TEXT_SIZE_BODY, false);
        mTableHeaderPaint = createTextPaint(TEXT_SIZE_TABLE, true);
        mTableCellPaint = createTextPaint(TEXT_SIZE_TABLE, false);
        mSmallPaint = createTextPaint(TEXT_SIZE_SMALL, false);
        mRowRulePaint = createRulePaint(RULE_WIDTH_ROW);
        mHeaderRulePaint = createRulePaint(RULE_WIDTH_HEADER);
        mFooterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFooterPaint.setColor(0xFF000000);
        mFooterPaint.setTextAlign(Paint.Align.CENTER);
        mFooterPaint.setTextSize(TEXT_SIZE_SMALL);
    }

    /**
     * Write the report data of a profile into a PDF file.
     *
     * @param outFile    the file to write the PDF to
     * @param reportData the assembled report data of a single profile
     * @return the written file
     * @throws IOException when failed to write the document
     */
    public File export(File outFile, PdfReportData reportData) throws IOException {
        // pass 1: measure every element and paginate, no document is created
        List<Element> elements = buildElements(reportData);
        List<PageLayout> pages = paginate(elements);
        // pass 2: render the pages, the total count is known up front
        render(outFile, pages);
        return outFile;
    }

    // ------------------------------------------------------------------ content building

    private List<Element> buildElements(PdfReportData reportData) {
        List<Element> elements = new ArrayList<>();
        buildHeaderBlock(elements, reportData);
        elements.add(new SpacerElement(SPACING_SECTION));
        buildMedicationsSection(elements, reportData);
        elements.add(new SpacerElement(SPACING_SECTION));
        buildAdherenceSection(elements, reportData);
        elements.add(new SpacerElement(SPACING_SECTION));
        buildIntakeHistorySection(elements, reportData);
        return elements;
    }

    private void buildHeaderBlock(List<Element> elements, PdfReportData reportData) {
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_title), mTitlePaint, CONTENT_WIDTH)));
        elements.add(new TextElement(createStaticLayout(
                orEmpty(reportData.getProfile().name), mProfilePaint, CONTENT_WIDTH)));
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_generated_at,
                        formatDateTime(new Date(), GENERATED_AT_FORMAT)),
                mBodyPaint, CONTENT_WIDTH)));
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_period, formatPeriod(reportData)),
                mBodyPaint, CONTENT_WIDTH)));
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_patient_generated),
                mSmallPaint, CONTENT_WIDTH)));
    }

    private void buildMedicationsSection(List<Element> elements, PdfReportData reportData) {
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_section_medicines),
                mSectionPaint, CONTENT_WIDTH), true));
        float[] columnWidths = columnWidths(0.26f, 0.36f, 0.38f);
        RowElement headerRow = createRow(new String[]{
                mAppContext.getString(R.string.pdf_report_col_name),
                mAppContext.getString(R.string.pdf_report_col_description),
                mAppContext.getString(R.string.pdf_report_col_schedule),
        }, columnWidths, true, null);
        elements.add(headerRow);
        for (Medicine medicine : reportData.getMedicines()) {
            String scheduleText = reportData.getScheduleText(medicine.id);
            elements.add(createRow(new String[]{
                    orEmpty(medicine.name),
                    orEmpty(medicine.description),
                    scheduleText == null
                            ? mAppContext.getString(R.string.pdf_report_as_needed)
                            : scheduleText,
            }, columnWidths, false, headerRow));
        }
    }

    private void buildAdherenceSection(List<Element> elements, PdfReportData reportData) {
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_section_adherence),
                mSectionPaint, CONTENT_WIDTH), true));
        buildAdherenceSubBlock(elements, reportData.getAdherence7Days(),
                mAppContext.getString(R.string.pdf_report_range_7_days));
        elements.add(new SpacerElement(SPACING_SUB_BLOCK));
        buildAdherenceSubBlock(elements, reportData.getAdherence30Days(),
                mAppContext.getString(R.string.pdf_report_range_30_days));
        elements.add(new SpacerElement(SPACING_SUB_BLOCK));
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_adherence_window_note),
                mSmallPaint, CONTENT_WIDTH)));
    }

    private void buildAdherenceSubBlock(List<Element> elements, AdherenceReport report,
                                        String rangeLabel) {
        elements.add(new TextElement(createStaticLayout(rangeLabel, mSubHeaderPaint,
                CONTENT_WIDTH), true));
        if (report == null || report.isEmpty()) {
            elements.add(new TextElement(createStaticLayout(
                    mAppContext.getString(R.string.pdf_report_adherence_empty),
                    mBodyPaint, CONTENT_WIDTH)));
            return;
        }
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_adherence_overall,
                        formatAdherenceCounts(report.totalTaken, report.totalMissed,
                                report.totalExtra, report.adherencePercent)),
                mBodyPaint, CONTENT_WIDTH)));
        for (AdherenceReport.MedicineAdherence medicineAdherence : report.perMedicine) {
            elements.add(new TextElement(createStaticLayout(
                    orEmpty(medicineAdherence.medicineName) + ADHERENCE_LINE_SEPARATOR
                            + formatAdherenceCounts(medicineAdherence.taken,
                            medicineAdherence.missed, medicineAdherence.extra,
                            medicineAdherence.adherencePercent),
                    mBodyPaint, CONTENT_WIDTH)));
        }
    }

    private void buildIntakeHistorySection(List<Element> elements, PdfReportData reportData) {
        elements.add(new TextElement(createStaticLayout(
                mAppContext.getString(R.string.pdf_report_section_intakes),
                mSectionPaint, CONTENT_WIDTH), true));
        List<MedicineIntake> intakesInRange = reportData.getIntakesInRange();
        if (intakesInRange.isEmpty()) {
            elements.add(new TextElement(createStaticLayout(
                    mAppContext.getString(R.string.pdf_report_intakes_empty),
                    mBodyPaint, CONTENT_WIDTH)));
            return;
        }
        float[] columnWidths = columnWidths(0.16f, 0.12f, 0.24f, 0.48f);
        RowElement headerRow = createRow(new String[]{
                mAppContext.getString(R.string.pdf_report_col_date),
                mAppContext.getString(R.string.pdf_report_col_time),
                mAppContext.getString(R.string.pdf_report_col_medicine),
                mAppContext.getString(R.string.pdf_report_col_notes),
        }, columnWidths, true, null);
        elements.add(headerRow);
        for (MedicineIntake medicineIntake : intakesInRange) {
            Medicine medicine = reportData.findMedicineById(medicineIntake.medicineId);
            elements.add(createRow(new String[]{
                    formatDateTime(medicineIntake.takenDateTime, DATE_FORMAT),
                    formatDateTime(medicineIntake.takenDateTime, TIME_FORMAT),
                    medicine == null ? "" : orEmpty(medicine.name),
                    orEmpty(medicineIntake.description),
            }, columnWidths, false, headerRow));
        }
    }

    // ------------------------------------------------------------------ two-pass pagination

    /**
     * Distributes the measured elements over pages: an element that does not
     * fit into the remaining content area starts a new page, the whole
     * trailing keep-with-next chain (e.g. section title + table header) moves
     * along to the new page so a title never strands alone at the bottom of a
     * page, and a table's header row is repeated above its overflowing rows.
     * Every page reserves the identical footer strip, so the computed page
     * count is final.
     * Package-private and static so the pagination geometry is testable.
     */
    static List<PageLayout> paginate(List<Element> elements) {
        List<PageLayout> pages = new ArrayList<>();
        PageLayout currentPage = new PageLayout();
        float y = CONTENT_TOP;
        for (Element element : elements) {
            if (y + element.mHeight > CONTENT_BOTTOM && !currentPage.isEmpty()) {
                // pull back the whole trailing keep-with-next chain, not just
                // the last element: the dominant layout is
                // [sectionTitle(keep) -> headerRow(keep) -> dataRow], where
                // pulling only the header would strand the section title
                List<Element> movedList = new ArrayList<>();
                while (currentPage.isLastKeepWithNext()) {
                    Element moved = currentPage.removeLast();
                    y -= moved.mHeight;
                    movedList.add(0, moved);
                }
                pages.add(currentPage);
                currentPage = new PageLayout();
                y = CONTENT_TOP;
                for (Element moved : movedList) {
                    currentPage.place(moved, y);
                    y += moved.mHeight;
                }
                if (element instanceof RowElement) {
                    Element tableHeader = ((RowElement) element).mTableHeader;
                    if (tableHeader != null && tableHeader != element
                            && !currentPage.contains(tableHeader)) {
                        currentPage.place(tableHeader, y);
                        y += tableHeader.mHeight;
                    }
                }
            }
            currentPage.place(element, y);
            y += element.mHeight;
        }
        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }
        return pages;
    }

    private void render(File outFile, List<PageLayout> pages) throws IOException {
        PdfDocument document = new PdfDocument();
        try {
            int pageNumber = 1;
            for (PageLayout page : pages) {
                PdfDocument.Page pdfPage = document.startPage(
                        new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber)
                                .create());
                Canvas canvas = pdfPage.getCanvas();
                for (PlacedElement placed : page.mElements) {
                    int save = canvas.save();
                    canvas.translate(CONTENT_LEFT, placed.mY);
                    placed.mElement.draw(canvas);
                    canvas.restore();
                }
                // the footer must be stamped before the page is finished
                drawFooter(canvas, pageNumber, pages.size());
                document.finishPage(pdfPage);
                pageNumber++;
            }
            try (FileOutputStream fileOutputStream = new FileOutputStream(outFile)) {
                document.writeTo(fileOutputStream);
            }
        } finally {
            document.close();
        }
    }

    private void drawFooter(Canvas canvas, int pageNumber, int pageCount) {
        String footer = mAppContext.getString(R.string.pdf_report_page_format,
                pageNumber, pageCount);
        float baseline = CONTENT_BOTTOM + (FOOTER_HEIGHT + mFooterPaint.getTextSize()) / 2f;
        canvas.drawText(footer, PAGE_WIDTH / 2f, baseline, mFooterPaint);
    }

    // ------------------------------------------------------------------ helpers

    private static TextPaint createTextPaint(float textSize, boolean fakeBold) {
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF000000);
        textPaint.setTextSize(textSize);
        textPaint.setFakeBoldText(fakeBold);
        return textPaint;
    }

    private static Paint createRulePaint(float strokeWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF000000);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        return paint;
    }

    private static StaticLayout createStaticLayout(String text, TextPaint textPaint, float width) {
        int widthInt = Math.max(1, (int) width);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, widthInt)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, LINE_SPACING_MULT)
                    .setIncludePad(false)
                    .build();
        }
        return new StaticLayout(text, textPaint, widthInt, Layout.Alignment.ALIGN_NORMAL,
                LINE_SPACING_MULT, 0f, false);
    }

    private RowElement createRow(String[] texts, float[] columnWidths,
                                 boolean isHeader, RowElement tableHeader) {
        TextPaint textPaint = isHeader ? mTableHeaderPaint : mTableCellPaint;
        StaticLayout[] cells = new StaticLayout[texts.length];
        float maxCellHeight = 0f;
        for (int i = 0; i < texts.length; i++) {
            cells[i] = createStaticLayout(texts[i], textPaint, columnWidths[i]);
            maxCellHeight = Math.max(maxCellHeight, cells[i].getHeight());
        }
        return new RowElement(cells, columnWidths, tableHeader, isHeader,
                maxCellHeight + CELL_VERTICAL_PADDING * 2,
                isHeader ? mHeaderRulePaint : mRowRulePaint);
    }

    private static float[] columnWidths(float... fractions) {
        float[] widths = new float[fractions.length];
        for (int i = 0; i < fractions.length; i++) {
            widths[i] = CONTENT_WIDTH * fractions[i];
        }
        return widths;
    }

    private String formatPeriod(PdfReportData reportData) {
        return formatDateTime(new Date(reportData.getIntakeRangeStartMillis()), DATE_FORMAT)
                + PERIOD_SEPARATOR
                + formatDateTime(new Date(reportData.getIntakeRangeEndMillis()), DATE_FORMAT);
    }

    private static String formatDateTime(Date date, String pattern) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
    }

    private String formatAdherenceCounts(int taken, int missed, int extra, int adherencePercent) {
        if (extra > 0) {
            return mAppContext.getString(
                    R.string.pdf_report_adherence_counts_extra_format,
                    taken, missed, extra, adherencePercent);
        }
        return mAppContext.getString(R.string.pdf_report_adherence_counts_format,
                taken, missed, adherencePercent);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------ render model

    /**
     * A measured content element of known height that can draw itself
     * at the canvas origin. Package-private (with its fields) so the nested
     * subclasses and the pagination test can read them directly.
     */
    abstract static class Element {
        final float mHeight;
        final boolean mKeepWithNext;

        Element(float height, boolean keepWithNext) {
            mHeight = height;
            mKeepWithNext = keepWithNext;
        }

        abstract void draw(Canvas canvas);
    }

    private static class TextElement extends Element {
        private final StaticLayout mLayout;

        private TextElement(StaticLayout layout) {
            this(layout, false);
        }

        private TextElement(StaticLayout layout, boolean keepWithNext) {
            super(layout.getHeight(), keepWithNext);
            mLayout = layout;
        }

        @Override
        void draw(Canvas canvas) {
            mLayout.draw(canvas);
        }
    }

    private static class SpacerElement extends Element {
        private SpacerElement(float height) {
            super(height, false);
        }

        @Override
        void draw(Canvas canvas) {
            // leave blank
        }
    }

    /**
     * One table row of wrapped text cells with a hairline rule below it.
     * Holds a reference to its table's header row so the pagination can
     * repeat the header when the row overflows to a new page.
     * Package-private so the pagination test can build rows.
     */
    static class RowElement extends Element {
        private final StaticLayout[] mCells;
        private final float[] mColumnWidths;
        private final Element mTableHeader;
        private final boolean mIsHeader;
        private final Paint mRulePaint;

        RowElement(StaticLayout[] cells, float[] columnWidths,
                   Element tableHeader, boolean isHeader, float height,
                   Paint rulePaint) {
            super(height, isHeader);
            mCells = cells;
            mColumnWidths = columnWidths;
            mTableHeader = tableHeader;
            mIsHeader = isHeader;
            mRulePaint = rulePaint;
        }

        @Override
        void draw(Canvas canvas) {
            float x = 0f;
            for (int i = 0; i < mCells.length; i++) {
                if (mCells[i] == null) {
                    continue;
                }
                int save = canvas.save();
                canvas.translate(x, CELL_VERTICAL_PADDING);
                mCells[i].draw(canvas);
                canvas.restore();
                x += mColumnWidths[i];
            }
            canvas.drawLine(0f, mHeight - 1f, CONTENT_WIDTH, mHeight - 1f, mRulePaint);
        }
    }

    static class PlacedElement {
        final Element mElement;
        final float mY;

        PlacedElement(Element element, float y) {
            mElement = element;
            mY = y;
        }
    }

    static class PageLayout {
        final List<PlacedElement> mElements = new ArrayList<>();

        private boolean isEmpty() {
            return mElements.isEmpty();
        }

        private boolean isLastKeepWithNext() {
            return !mElements.isEmpty()
                    && mElements.get(mElements.size() - 1).mElement.mKeepWithNext;
        }

        private void place(Element element, float y) {
            mElements.add(new PlacedElement(element, y));
        }

        private Element removeLast() {
            return mElements.remove(mElements.size() - 1).mElement;
        }

        private boolean contains(Element element) {
            for (PlacedElement placed : mElements) {
                if (placed.mElement == element) {
                    return true;
                }
            }
            return false;
        }
    }
}
