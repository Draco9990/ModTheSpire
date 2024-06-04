package com.evacipated.cardcrawl.modthespire.ui;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Handles all UI related functionality of Import and Export.
 */
public class ImportExportUI {
    /** File extension given to all exported modlist files. */
    private static final String exportedModlistFileExtension = ".mtsmodlist";


    /**
     * Opens the 'No Mods to Export' export window.
     * @param owner : Owner component of the new window.
     */
    public static void openNoModsToExportWindow(Component owner){
        JOptionPane.showMessageDialog(owner, "Failed to export modlist, no mods were selected.", "Failure", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Opens the 'Failed Serialization' export window.
     * @param owner : Owner component of the new window.
     */
    public static void openFailedSerializationExportWindow(Component owner){
        JOptionPane.showMessageDialog(owner, "Failed to export modlist, could not serialize the modlist.", "Failure", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Opens the 'Successful Export' export window. If requested, exports the modlist key to a file.
     * @param owner : Owner component of the new window.
     * @param serializedModlist : Serialized modlist key.
     */
    public static void openSuccessfulExportWindow(Component owner, String serializedModlist){
        String[] options = new String[]{"Save to file", "Close"};

        int result = JOptionPane.showOptionDialog(
            owner,
            "Modlist key has been copied to clipboard!",
            "Success",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[1]
        );

        if(result == 0){
            // User has requested to export to file
            JFileChooser fileChooser = new JFileChooser();

            int saveDialogResult = fileChooser.showSaveDialog(owner);
            if (saveDialogResult == JFileChooser.APPROVE_OPTION) {
                // Get the user selected file and append the file extension to it
                File selectedFile = fileChooser.getSelectedFile();

                String filePath = selectedFile.getAbsolutePath();
                if (!filePath.endsWith(exportedModlistFileExtension)) {
                    filePath += exportedModlistFileExtension;
                    selectedFile = new File(filePath);
                }

                // Write to file
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(selectedFile))) {
                    writer.write(serializedModlist);
                } catch (IOException e) {
                    System.out.println("Failed to write modlist to file due to " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}
