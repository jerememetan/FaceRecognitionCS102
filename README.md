/\
--------------------------------------------------------------------------
Package Structure and Key Classes
--------------------------------------------------------------------------

The application follows a modular package structure organized as follows:
## app
- **FaceRecognitionApp** Face Recognition App
- **StudentManager** Student Manager System
### config
- **AppConfig**: Manages application configuration settings, including detection parameters and system preferences. All config is always stored and saved into app.properties
- **AppLogger**: Handles logging throughout the application.
- **IConfigChangeListener**: Interface for listening to configuration changes.

### entity
- **Student**: Represents student data entities with ID, name, and other attributes.
- **Session**: Session data, eg(CS102 G1 Week1)
- **Roster**: Roster data, eg(CS102 G1)
### gui (Graphical User Interface)
- **config**
  - **FaceCropSettingsPanel**: UI panel for configuring face cropping settings.
- **detection**
  - Face detection related UI components.
- **homepage**
  - **LoginPage**: Main login interface for user authentication.
  - **MainDashboard**: Primary dashboard after login.
  - **SettingsGUI**: Settings and configuration interface.
  - **ReportsGUI**: Reports history interface
- **recognition**
  - **CameraPanel**: Displays camera feed and handles video processing.
  - **LiveRecognitionViewer**: UI for live face recognition functionality.
- **settings**
  - **SettingsCenter**: Central hub for managing different settings panels.
  - **WelcomeView**: Welcome screen in settings.

### model
- **FaceData**: Storing of FaceData of images
- **FaceCandidate**: A detected Face from a camera
- **FaceImage**: image and embedding

### repository
- **DBConnection**: Link to Database
- **StudentRepositoryInstance**: CRUD functionality for Student Database
- **SessionRepositoryInstance**: CRUD functionality for Session Database
- **RosterRepositoryInstance**: CRUD functionality for Roster Database
- the rest has relational CRUD functionality for cross-tables

### report
- **CSVGenerator**: Generates CSV reports.
- **ExcelGenerator**: Generates Excel reports.
- **PDFGenerator**: Generates PDF reports.
- **StudentData**: Handles student data for reporting.
- **ReportLog**: Reporting Logging


### service
- **embedding**
  - **FaceEmbeddingGenerator**: Generates face embeddings for recognition.
  - **EmbeddingQualityAnalyzer**: Analyzes quality of generated embeddings.
- **recognition**
  - **LiveRecognitionService**: Core service for live face recognition.
- **session**
  - **SessionManager**: Manages user sessions.
- **student**
  - **StudentManager**: Manages student-related operations.

### util
- **ImageProcessor**: Utility class for image processing operations.
- **ColourTheme**: Utility class for standardising colours
*

--------------------------------------------------------------------------
How to Run the Application
--------------------------------------------------------------------------

### Prerequisites
- Java Development Kit (JDK) installed
- OpenCV library files in `lib/` directory
- Windows environment (batch files provided)
- Ensure arcface.onnx is downloaded in data/resources (download link: https://huggingface.co/garavv/arcface-onnx)

### Steps to Run
1. **Compile the Application**:
   - Run `Compile.bat`(Windows) or `Compile.sh`(mac) to compile all Java source files. 
   - This compiles the source from `src/` directory to `compiled/` directory.

2. **Run the Application**:
   - Run `run.bat`(Windows) or `run.sh`(mac) to start the application.
   - This launches the login page interface.
   For Admin, username: 123, password: admin123
   For TA, username: 456, password: TA123


### Notes
- Ensure OpenCV DLL is loaded via `System.load(new File("lib/opencv_java480.dll").getAbsolutePath());` in relevant classes.
- VS Code settings should include referenced libraries in `.vscode/settings.json`.

--------------------------------------------------------------------------
Make openCV .jar and .dll accessible from lib/* for any local machine
--------------------------------------------------------------------------

1) In .vscode/settings.json, in java.project.referencedLibraries, we call .jar no issue
2) In both all java files, we do:

    System.load(new File("lib/opencv_java480.dll").getAbsolutePath());

    Which is an indirect direct way to get the absolute path of the file. 


-------------------------------------
Debugging and Custom Configurations (Developer Settings)
-------------------------------------
Logs are generated in /logs
Custom Configurations and further fine_tuning can be done by modifying app.properties file

