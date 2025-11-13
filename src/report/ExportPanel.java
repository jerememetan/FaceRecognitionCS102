package report;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import util.ColourTheme;

public class ExportPanel extends JFrame {

    private JButton exportCsvButton;
    private JButton exportExcelButton;
    private JButton exportPdfButton;
    private JTable dataTable;
    private ReportBuilder reportBuilder;
    private JPanel headerSelectionPanel;
    private String reportTitle; // new title field

    // Modified constructor to take title
    public ExportPanel(String title, ArrayList<String> headers, ArrayList<ArrayList<String>> data) {
        this.reportTitle = title; // assign title
        // Initialize ReportBuilder
        reportBuilder = new ReportBuilder(headers, data);

        setTitle("Report Export Panel");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(850, 500);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // ===== HEADER SELECTION PANEL =====
        headerSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        headerSelectionPanel.setBackground(new Color(245, 245, 245));
        headerSelectionPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), "Select Headers"));

        for (String header : headers) {
            JCheckBox checkBox = new JCheckBox(header, true); // initially all selected
            checkBox.setBackground(new Color(245, 245, 245));
            checkBox.setFocusPainted(false);
            checkBox.addActionListener(e -> {
                if (checkBox.isSelected()) {
                    reportBuilder.addHeader(header);
                } else {
                    reportBuilder.removeHeader(header);
                }
                refreshTable();
            });
            headerSelectionPanel.add(checkBox);
        }

        JScrollPane headerScroll = new JScrollPane(headerSelectionPanel);
        headerScroll.setPreferredSize(new Dimension(820, 70));
        headerScroll.setBorder(BorderFactory.createEmptyBorder());
        add(headerScroll, BorderLayout.NORTH);

        // ===== TABLE SECTION =====
        dataTable = new JTable();
        refreshTable(); // populate table initially
        dataTable.setFillsViewportHeight(true);
        dataTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        dataTable.setRowHeight(28);
        dataTable.setSelectionBackground(new Color(99, 102, 241, 120));
        dataTable.setGridColor(new Color(220, 220, 220));
        dataTable.setShowGrid(true);
        dataTable.setShowHorizontalLines(true);
        dataTable.setShowVerticalLines(false);
        dataTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        dataTable.getTableHeader().setBackground(new Color(230, 230, 230));
        dataTable.getTableHeader().setOpaque(true);

        // Alternating row colors
        dataTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 245, 245));
                }
                return this;
            }
        });

        JScrollPane scrollPane = new JScrollPane(dataTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        add(scrollPane, BorderLayout.CENTER);

        // ===== EXPORT BUTTON PANEL =====
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        exportPanel.setBackground(new Color(245, 245, 245));

        exportCsvButton = createAccentButton("📊 Export CSV", ColourTheme.PRIMARY_COLOR);
        exportExcelButton = createAccentButton("📈 Export Excel", new Color(16, 185, 129));
        exportPdfButton = createAccentButton("📄 Export PDF", ColourTheme.DANGER);

        exportCsvButton.setActionCommand("Export CSV");
        exportExcelButton.setActionCommand("Export Excel");
        exportPdfButton.setActionCommand("Export PDF");

        ActionListener actionHandler = e -> {
            String command = e.getActionCommand();
            switch (command) {
                case "Export Excel":
                    ExcelGenerator excelGen = new ExcelGenerator(
                            reportBuilder.getSelectedHeaders(),
                            reportBuilder.getSelectedData(),
                            reportTitle // pass title
                    );
                    boolean excelSuccess = excelGen.generate();
                    JOptionPane.showMessageDialog(ExportPanel.this,
                            excelSuccess ? "Excel report generated successfully!"
                                    : "Excel export cancelled or failed.",
                            excelSuccess ? "Export Complete" : "Export Cancelled",
                            excelSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    break;

                case "Export PDF":
                    PDFGenerator pdfGen = new PDFGenerator(
                            reportBuilder.getSelectedHeaders(),
                            reportBuilder.getSelectedData(),
                            reportTitle // pass title
                    );
                    boolean pdfSuccess = pdfGen.generate();
                    JOptionPane.showMessageDialog(ExportPanel.this,
                            pdfSuccess ? "PDF report generated successfully!"
                                    : "PDF export cancelled or failed.",
                            pdfSuccess ? "Export Complete" : "Export Cancelled",
                            pdfSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    break;

                case "Export CSV":
                    CSVGenerator csvGen = new CSVGenerator(
                            reportBuilder.getSelectedHeaders(),
                            reportBuilder.getSelectedData(),
                            reportTitle
                    );
                    boolean csvSuccess = csvGen.generate();
                    JOptionPane.showMessageDialog(ExportPanel.this,
                            csvSuccess ? "CSV report generated successfully!"
                                    : "CSV export cancelled or failed.",
                            csvSuccess ? "Export Complete" : "Export Cancelled",
                            csvSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    break;

                default:
                    JOptionPane.showMessageDialog(ExportPanel.this,
                            "Unknown export action.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
            }
        };

        exportCsvButton.addActionListener(actionHandler);
        exportExcelButton.addActionListener(actionHandler);
        exportPdfButton.addActionListener(actionHandler);

        exportPanel.add(exportCsvButton);
        exportPanel.add(exportExcelButton);
        exportPanel.add(exportPdfButton);

        add(exportPanel, BorderLayout.SOUTH);
    }

    // ===== Refresh JTable based on currently selected headers =====
    private void refreshTable() {
        ArrayList<String> selectedHeaders = reportBuilder.getSelectedHeaders();
        ArrayList<ArrayList<String>> selectedData = reportBuilder.getSelectedData();

        DefaultTableModel tableModel = new DefaultTableModel(selectedHeaders.toArray(new String[0]), 0);
        for (ArrayList<String> row : selectedData) {
            tableModel.addRow(row.toArray(new String[0])); // no remapping needed
        }
        dataTable.setModel(tableModel);
    }

    // ===== Helper to create styled buttons =====
    private JButton createAccentButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setPreferredSize(new Dimension(150, 40));
        button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
}
