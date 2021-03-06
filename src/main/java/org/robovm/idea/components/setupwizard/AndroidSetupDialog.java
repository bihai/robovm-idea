package org.robovm.idea.components.setupwizard;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.zeroturnaround.zip.NameMapper;
import org.zeroturnaround.zip.ZipUtil;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;

import javax.swing.*;

public class AndroidSetupDialog extends JDialog {
    private static final String ANDROID_SDK_URL = "http://dl.google.com/android/android-sdk_r24.2-macosx.zip";
    private JPanel panel;
    private JLabel infoText;
    private JButton nextButton;
    private JButton installAndroidSdkButton;
    private JButton browseButton;
    private JTextField sdkLocation;
    private JPanel installPanel;

    public AndroidSetupDialog() {
        setContentPane(panel);
        setModalityType(ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("RoboVM Setup");
        infoText.setText("<html>Install the Android SDK if you want to develop for both iOS and Android.</br>");

        sdkLocation.setText(System.getProperty("user.home") + "/Library/Android/sdk");

        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
                    @Override
                    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
                        return file.isDirectory();
                    }

                    @Override
                    public boolean isFileSelectable(VirtualFile file) {
                        return file.isDirectory();
                    }
                };
                FileChooserDialog fileChooser = FileChooserFactory.getInstance().createFileChooser(fileChooserDescriptor, null, panel);
                VirtualFile location = null;
                if (new File(sdkLocation.getText()).exists()) {
                    location = LocalFileSystem.getInstance().findFileByIoFile(new File(sdkLocation.getText()));
                }
                VirtualFile[] dir = fileChooser.choose(null, location);
                if (dir != null && dir.length > 0) {
                    sdkLocation.setText(dir[0].getCanonicalPath());
                    boolean validSdk = isAndroidSdkInstalled();
                    installAndroidSdkButton.setVisible(!validSdk);
                }
            }
        });

        installAndroidSdkButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                installAndroidSdk();
            }
        });

        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        if(isAndroidSdkInstalled()) {
            nextButton.setText("Next");
            installAndroidSdkButton.setVisible(false);
        }

        pack();
        setLocationRelativeTo(null);
    }

    private boolean isAndroidSdkInstalled() {
        File sdk = new File(sdkLocation.getText(), "tools/android");
        return sdk.exists();
    }

    private void installAndroidSdk() {
        sdkLocation.setEnabled(false);
        nextButton.setEnabled(false);
        browseButton.setEnabled(false);

        downloadAndroidSdk();
    }

    private void downloadAndroidSdk() {
        installPanel.removeAll();
        GridLayoutManager layout = new GridLayoutManager(2, 1);
        installPanel.setLayout(layout);

        final JLabel label = new JLabel("Downloading Android SDK ...");
        final JProgressBar progressBar = new JProgressBar();
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);

        GridConstraints labelConsts = new GridConstraints();
        labelConsts.setFill(GridConstraints.FILL_HORIZONTAL);
        GridConstraints progressConsts = new GridConstraints();
        progressConsts.setRow(1);
        progressConsts.setFill(GridConstraints.FILL_HORIZONTAL);

        installPanel.add(label, labelConsts);
        installPanel.add(progressBar, progressConsts);
        installPanel.revalidate();

        installAndroidSdkButton.setText("Cancel");
        for(ActionListener listener: installAndroidSdkButton.getActionListeners()) {
            installAndroidSdkButton.removeActionListener(listener);
        }
        final BooleanFlag cancel = new BooleanFlag();
        installAndroidSdkButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel.setValue(true);
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                File destination = null;
                try {
                    // download the SDK zip to a temporary location
                    //destination = new File("/var/folders/x5/833s8nbn1953zc403mr8sj800000gn/T/android-sdk2784309449223743597.zip");
                    destination = File.createTempFile("android-sdk", ".zip");
                    System.out.println(destination);
                    URL url = new URL(ANDROID_SDK_URL);
                    URLConnection con = url.openConnection();
                    long length = con.getContentLengthLong();
                    byte[] buffer = new byte[1024*100];
                    try (InputStream in = con.getInputStream(); OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
                        int read = in.read(buffer);
                        int total = read;
                        while(read != -1) {
                            out.write(buffer, 0, read);
                            read = in.read(buffer);
                            total += read;
                            final int percentage = (int)(((double)total / (double)length) * 100);
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setValue(Math.min(100, percentage));
                                }
                            });

                            if(cancel.getValue()) {
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        installAndroidSdkButton.setText("Install Android SDK");
                                        for(ActionListener listener: installAndroidSdkButton.getActionListeners()) {
                                            installAndroidSdkButton.removeActionListener(listener);
                                        }
                                        installAndroidSdkButton.addActionListener(new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                installAndroidSdk();
                                            }
                                        });
                                        installPanel.removeAll();
                                        installPanel.revalidate();
                                        browseButton.setEnabled(true);
                                        nextButton.setEnabled(true);
                                    }
                                });
                                break;
                            }
                        }
                    }

                    // unpack the SDK zip, then rename the extracted
                    // folder
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            label.setText("Unpacking SDK to " + sdkLocation.getText());
                            installAndroidSdkButton.setEnabled(false);
                            nextButton.setEnabled(false);
                        }
                    });
                    final File outputDir = new File(sdkLocation.getText());
                    File tmpOutputDir = new File(outputDir.getParent());
                    if(!tmpOutputDir.exists()) {
                        if(!tmpOutputDir.mkdirs()) {
                            throw new RuntimeException("Couldn't create output directory");
                        }
                    }
                    ZipUtil.unpack(destination, tmpOutputDir, new NameMapper() {
                        @Override
                        public String map(String s) {
                            int idx = s.indexOf("/");
                            s = outputDir.getName() + s.substring(idx);
                            final String file = s;
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    label.setText("Unpacking " + file.substring(0, Math.min(50, file.length()-1)) + " ...");
                                }
                            });
                            return s;
                        }
                    });

                    // done with unpacking, let's show the components
                    // installer wizard
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            System.exit(-1);
                        }
                    });
                } catch (final Throwable e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            label.setForeground(Color.red);
                            label.setText("Couldn't install Android SDK: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } finally {
                    if(destination != null) {
                        destination.delete();
                    }
                }
            }
        }).start();
    }

    private static class BooleanFlag {
        volatile boolean value;

        public void setValue(boolean value) {
            this.value = value;
        }

        public boolean getValue() {
            return this.value;
        }
    }

    public static boolean isAndroidSdkSetup() {
        for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
            if (sdk.getSdkType().getName().equals("Android SDK")) {
                return true;
            }
        }
        return false;
    }

    private void createUIComponents() {}

    public static void main(String[] args) {
        AndroidSetupDialog dialog = new AndroidSetupDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }
}
