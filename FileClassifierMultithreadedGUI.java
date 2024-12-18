package project_;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * FileClassifierMultithreadedGUI:
 * A graphical application that classifies files in a selected folder based on their type/extension.
 * It utilizes multithreading to process files efficiently and provides visual feedback through a GUI.
 */
public class FileClassifierMultithreadedGUI extends JFrame {

    // Serialization ID to prevent warnings during the serialization process.
    private static final long serialVersionUID = 1L;

    // GUI Components
    private JTextField folderPathField;   // Input field to display or enter the folder path
    private JTextField excludeFileField; // Input field to specify a file to exclude
    private JTextArea resultArea;        // Output area to display classification logs and progress

    // Map to track counts of different file types for visualization
    private Map<String, Integer> fileTypeCounts = new HashMap<>();

    // Thread pool for executing file operations concurrently
    private ExecutorService executor;

    /**
     * Constructor:
     * Initializes the GUI, its components, and the layout.
     */
    public FileClassifierMultithreadedGUI() {
        super("File Classifier"); // Set the window title

        // Initialize GUI components
        folderPathField = new JTextField(30); // Field to display folder path
        JButton browseButton = new JButton("Browse"); // Button for folder selection
        JButton classifyButton = new JButton("Classify Files"); // Button to start classification
        JButton visualizeButton = new JButton("Visualize Statistics"); // Button to display bar chart
        resultArea = new JTextArea(10, 40); // Text area for logging output
        resultArea.setEditable(false); // Make it read-only
        JScrollPane resultScrollPane = new JScrollPane(resultArea); // Add scroll functionality to the text area
        excludeFileField = new JTextField(30); // Field to specify a file to exclude from classification

        // Configure the layout using GridBagLayout
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Folder selection components
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(new JLabel("Folder Path:"), gbc);

        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        add(folderPathField, gbc);

        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        add(browseButton, gbc);

        // Exclude file components
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        add(new JLabel("Exclude File:"), gbc);

        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        add(excludeFileField, gbc);

        // Classification and visualization buttons
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 6;
        add(classifyButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 6;
        add(visualizeButton, gbc);

        // Log output area
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 6;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        add(resultScrollPane, gbc);

        // Browse button: Allows the user to select a folder
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // Restrict selection to directories only
            int result = fileChooser.showOpenDialog(FileClassifierMultithreadedGUI.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFolder = fileChooser.getSelectedFile();
                folderPathField.setText(selectedFolder.getAbsolutePath()); // Display the selected folder path
            }
        });

        // Classify button: Starts the file classification process
        classifyButton.addActionListener(e -> {
            String baseFolderPath = folderPathField.getText();
            File baseFolder = new File(baseFolderPath);

            // Validate the selected folder
            if (!baseFolder.exists() || !baseFolder.isDirectory()) {
                resultArea.append("Specified folder does not exist!\n");
                return;
            }

            String excludeFilePath = excludeFileField.getText();
            File excludeFile = new File(excludeFilePath);

            long startTime = System.currentTimeMillis(); // Record start time for performance tracking
            fileTypeCounts.clear(); // Clear previous file type counts

            // Initialize thread pool based on the number of available processors
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            // List and classify files
            List<String> files = listFiles(baseFolder, excludeFile);
            Map<String, List<String>> categories = classifyFiles(files);

            // Process each category in its own thread
            for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
                executor.submit(() -> createFoldersAndMoveFiles(entry, baseFolder));
            }

            // Wait for all threads to complete
            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException ex) {
                resultArea.append("Error: Classification interrupted!\n");
            }

            // Delete empty folders after classification
            deleteEmptyFolders(baseFolder);

            long endTime = System.currentTimeMillis(); // Record end time
            resultArea.append("Classification complete!\n");
            resultArea.append("Total files classified: " + files.size() + "\n");
            resultArea.append("Time taken: " + (endTime - startTime) + " ms\n");
        });

        // Visualize button: Displays a bar chart of file type distribution
        visualizeButton.addActionListener(e -> {
            JFrame chartFrame = new JFrame("File Type Distribution");
            chartFrame.setSize(600, 400);
            chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JPanel chartPanel = new JPanel() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    int barWidth = 50;
                    int barSpacing = 20;
                    int maxHeight = 300;

                    // Find the maximum file count for scaling
                    int maxFileCount = fileTypeCounts.values().stream().max(Integer::compare).orElse(1);

                    int x = 50; // Initial x-coordinate for bars
                    for (Map.Entry<String, Integer> entry : fileTypeCounts.entrySet()) {
                        int fileCount = entry.getValue();
                        int barHeight = (int) ((double) fileCount / maxFileCount * maxHeight);

                        g.setColor(Color.BLUE);
                        g.fillRect(x, 350 - barHeight, barWidth, barHeight); // Draw bar

                        g.setColor(Color.BLACK);
                        g.drawString(entry.getKey(), x, 370); // Draw file type label
                        g.drawString(String.valueOf(fileCount), x + 10, 350 - barHeight - 10); // Draw count
                        x += (barWidth + barSpacing); // Move x-coordinate for next bar
                    }
                }
            };

            chartFrame.add(chartPanel); // Add chart panel to the frame
            chartFrame.setVisible(true); // Display the frame
        });

        // Finalize window setup
        pack();
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    /**
     * Recursively lists all files in the folder and excludes the specified file.
     */
    private List<String> listFiles(File folder, File excludeFile) {
        List<String> files = new ArrayList<>();
        for (File file : folder.listFiles()) {
            if (file.isFile() && !file.equals(excludeFile)) {
                files.add(file.getAbsolutePath());
                String fileExtension = getFileExtension(file.getName());
                synchronized (fileTypeCounts) {
                    fileTypeCounts.put(fileExtension, fileTypeCounts.getOrDefault(fileExtension, 0) + 1);
                }
            } else if (file.isDirectory()) {
                files.addAll(listFiles(file, excludeFile));
            }
        }
        return files;
    }

    /**
     * Groups files by their extensions.
     */
    private Map<String, List<String>> classifyFiles(List<String> fileList) {
        Map<String, List<String>> categories = new HashMap<>();
        for (String filePath : fileList) {
            File file = new File(filePath);
            String fileExtension = getFileExtension(file.getName()).toLowerCase();
            categories.putIfAbsent(fileExtension, new ArrayList<>());
            categories.get(fileExtension).add(filePath);
        }
        return categories;
    }

    /**
     * Moves files to corresponding folders based on their type.
     */
    private void createFoldersAndMoveFiles(Map.Entry<String, List<String>> entry, File baseFolder) {
        String category = entry.getKey();
        List<String> files = entry.getValue();

        File categoryFolder = new File(baseFolder, category);
        if (!categoryFolder.exists()) {
            categoryFolder.mkdirs(); // Create folder if it doesn't exist
        }

        for (String filePath : files) {
            File sourceFile = new File(filePath);
            File destFile = new File(categoryFolder, sourceFile.getName());
            try {
                Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                synchronized (resultArea) {
                    resultArea.append("Moved " + sourceFile.getName() + " to " + categoryFolder.getName() + "\n");
                }
            } catch (IOException e) {
                synchronized (resultArea) {
                    resultArea.append("Error moving file " + sourceFile.getName() + ": " + e.getMessage() + "\n");
                }
            }
        }
    }

    /**
     * Deletes empty folders in the directory tree.
     */
    private void deleteEmptyFolders(File folder) {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                deleteEmptyFolders(file);
                if (file.listFiles().length == 0) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Extracts the file extension from a file name.
     */
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "unknown"; // Return "unknown" if no extension is found
    }

    /**
     * Main method to launch the application.
     */
    public static void main(String[] args) {
        new FileClassifierMultithreadedGUI(); // Instantiate and display the GUI
    }
}
    