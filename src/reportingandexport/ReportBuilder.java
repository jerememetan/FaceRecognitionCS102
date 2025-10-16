package reportingandexport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReportBuilder {
    private List<String> fieldHeaders;
    private List<List<String>> data = new ArrayList<>();

    public List<List<String>> getFullData(){
        List<List<String>> fullData = new ArrayList<>();
        fullData.add(getFieldHeaders());
        fullData.addAll(getData());
        return fullData;
    }

    public List<String> getFieldHeaders(){
        return this.fieldHeaders;
    }

    public List<List<String>> getData(){
        return this.data;
    }

    public void initializeFieldHeaders(List<String> initialFieldHeaders){
        this.fieldHeaders = initialFieldHeaders;
    }

    public void initializeData(List<StudentData> initialData){
        for (StudentData student : initialData) { 
            this.data.add(new ArrayList<>(Arrays.asList(
                student.getStudentID().toString(),
                student.getName(),
                student.getStatus(),
                student.getTimestamp(),
                String.valueOf(student.getConfidence()),
                student.getMethod(),
                student.getNotes()
                ))
            );
        }
    }

    public void addFieldHeaders(String newFieldHeader){
        this.fieldHeaders.add(newFieldHeader);
    }

    public void removeFieldHeaders(String oldFieldHeader){
        this.fieldHeaders.remove(oldFieldHeader);
    }
}
