package report;

import java.util.ArrayList;

public class ReportBuilder {

    private final ArrayList<String> fixedHeaders; // all allowed headers
    private final ArrayList<ArrayList<String>> data; // full data
    private final ArrayList<String> selectedHeaders; // currently selected headers

    public ReportBuilder(ArrayList<String> fixedHeaders, ArrayList<ArrayList<String>> data) {
        this.fixedHeaders = new ArrayList<>(fixedHeaders);
        this.data = data;
        this.selectedHeaders = new ArrayList<>(fixedHeaders); // start with all headers
    }

    /**
     * Return all original headers (fixed headers)
     */
    public ArrayList<String> getAllHeaders() {
        return new ArrayList<>(fixedHeaders);
    }

    /**
     * Remove a header from selected headers
     */
    public void removeHeader(String header) {
        selectedHeaders.remove(header);
    }

    /**
     * Add a header back to selected headers in its original order
     */
    public void addHeader(String header) {
        if (fixedHeaders.contains(header) && !selectedHeaders.contains(header)) {
            // Maintain original order
            int idx = 0;
            for (; idx < selectedHeaders.size(); idx++) {
                if (fixedHeaders.indexOf(selectedHeaders.get(idx)) > fixedHeaders.indexOf(header)) {
                    break;
                }
            }
            selectedHeaders.add(idx, header);
        }
    }

    /**
     * Get currently selected headers
     */
    public ArrayList<String> getSelectedHeaders() {
        return new ArrayList<>(selectedHeaders);
    }

    /**
     * Get data filtered by currently selected headers
     */
    public ArrayList<ArrayList<String>> getSelectedData() {
        ArrayList<ArrayList<String>> filteredData = new ArrayList<>();

        // find indices of selected headers in fixedHeaders
        ArrayList<Integer> indices = new ArrayList<>();
        for (String header : selectedHeaders) {
            indices.add(fixedHeaders.indexOf(header));
        }

        for (ArrayList<String> row : data) {
            ArrayList<String> filteredRow = new ArrayList<>();
            for (int idx : indices) {
                filteredRow.add(row.get(idx));
            }
            filteredData.add(filteredRow);
        }

        return filteredData;
    }
}
