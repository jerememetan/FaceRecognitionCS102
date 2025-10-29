package app.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;

public class StudentSearchFilter {
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;

    public void setTableModel(DefaultTableModel tableModel) {
        this.tableModel = tableModel;
    }

    public void setRowSorter(TableRowSorter<DefaultTableModel> rowSorter) {
        this.rowSorter = rowSorter;
    }

    public void performSearch(String searchTerm, TableRowSorter<DefaultTableModel> rowSorter, JLabel statusLabel) {
        this.rowSorter = rowSorter;
        if (searchTerm.trim().isEmpty()) {
            rowSorter.setRowFilter(null);
            statusLabel.setText("Showing all students");
        } else {
            rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchTerm));
            statusLabel.setText("Search results for: " + searchTerm);
        }
    }

    public void clearSearch(JTextField searchField, TableRowSorter<DefaultTableModel> rowSorter, JLabel statusLabel) {
        this.rowSorter = rowSorter;
        searchField.setText("");
        rowSorter.setRowFilter(null);
        statusLabel.setText("Showing all students");
    }

    public void filterStudents(String filter, TableRowSorter<DefaultTableModel> rowSorter, JLabel statusLabel) {
        this.rowSorter = rowSorter;
        switch (filter) {
            case "all":
                rowSorter.setRowFilter(null);
                statusLabel.setText("Showing all students");
                break;
            case "with_faces":
                rowSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                    @Override
                    public boolean include(javax.swing.RowFilter.Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        Integer imageCount = (Integer) entry.getValue(4);
                        return imageCount > 0;
                    }
                });
                statusLabel.setText("Showing students with face images");
                break;
            case "without_faces":
                rowSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                    @Override
                    public boolean include(javax.swing.RowFilter.Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        Integer imageCount = (Integer) entry.getValue(4);
                        return imageCount == 0;
                    }
                });
                statusLabel.setText("Showing students without face images");
                break;
        }
    }

    public void applyFilter() {
        // This method can be used to reapply current filters if needed
    }
}