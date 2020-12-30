/*
BSD 3-Clause License

Copyright (c) 2020, Bram Stout
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/


package nl.bramstout.bump2roughness;

import java.io.File;
import java.text.DecimalFormat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import nl.bramstout.bump2roughness.Bump2Roughness.MAPTYPE;
import nl.bramstout.bump2roughness.Bump2Roughness.OUTPUTMODE;
import nl.bramstout.bump2roughness.Bump2Roughness.ProgressCallback;
import nl.bramstout.bump2roughness.Bump2Roughness.RENDERER;
import nl.bramstout.bump2roughness.Bump2Roughness.Settings;

public class MainWindow extends Application {

	private static final String DOC_RENDERER = "Which render engine you want to generate the textures for.\n\nEach render engine implements things in slightly different ways, so in order to get correct results, this tool needs to know which render engine you are using.";
	private static final String DOC_MAKETX_PATH = "The path to either Arnold's maketx, OpenImageIO's maketx or Renderman's txmake. maketx and txmake are utilities that generate TX or TEX textures. OpenImageIO's maketx is recommended. Arnold's maketx is not fully supported and could crash. OpenImageIO's maketx can easily be created on Windows using vcpkg and by installing \"OpenImageIO[tools]\"";
	private static final String DOC_OUTPUTMODE = "How the roughness textures should be exported.\n\nTX/TEX: Export a TX or TEX texture file containing the roughness texture.\n\nIndividual MIPMAP levels: Export a PNG image with the roughness texture for each mipmap level. This is useful if you want to combine it into a single texture file yourself or use it in a game engine where you specify the mip map levels individually.";
	private static final String DOC_UNITSIZE = "The size of the UV space compared in world units. Basically, how big is the texture in world coordinates. If you have a texture that goes from 0.0 to 1.0 in UV space, but spans a 3.0 meter range in the world space and a world unit is 1 meter, then the UV Unit World Size should be 3.0";
	private static final String DOC_GENERATE_DIFFUSE = "Whether or not it should generate a rougness texture for the diffuse lobe in the material. It is more accurate to do this, but isn't always done.";
	private static final String DOC_DIFFUSE_VALUE = "The base diffuse roughness value. The roughness values generated from the bump, normal and displacement maps are added on top of this. The value could either be a number that equals the material's diffuse roughness value or be a file path to a file that contains the diffuse roughness texture.";
	private static final String DOC_GENERATE_SPECULAR = "Whether or not it should generate a rougness texture for the specular lobe in the material.";
	private static final String DOC_SPECULAR_VALUE = "The base specular roughness value. The roughness values generated from the bump, normal and displacement maps are added on top of this. The value could either be a number that equals the material's specular roughness value or be a file path to a file that contains the specular roughness texture.";
	private static final String DOC_DIFFUSE_OUTPUT = "The path to write the diffuse roughness texture to. If the output mode is set to TX/TEX, then it will create a .tx or .tex file with the given name. If the output mode is Individual MIPMAP levels, then it exports multiple PNG files where it appends \"_[level].png\" at the end of the given path.";
	private static final String DOC_SPECULAR_OUTPUT = "The path to write the specular roughness texture to. If the output mode is set to TX/TEX, then it will create a .tx or .tex file with the given name. If the output mode is Individual MIPMAP levels, then it exports multiple PNG files where it appends \"_[level].png\" at the end of the given path.";
	private static final String DOC_TEX_PATH = "The path to either a bump, normal or displacement map for which you want to calculate the additional roughness from.";
	private static final String DOC_TEX_SCALE = "The scale or strength of the given map. This is the same value as you'd use in your DCC application.";
	private static final String DOC_TEX_TYPE = "What kind of a map this is. Different maps need to be interpreted in different ways.";

	public static Stage stage;

	private VBox root;
	private ChoiceBox<String> rendererControl;
	private TextField maketxPathControl;
	private ChoiceBox<String> outputModeControl;
	private TextField unitSizeControl;
	private CheckBox generateDiffuseControl;
	private TextField diffuseBaseValueControl;
	private CheckBox generateSpecularControl;
	private TextField specularBaseValueControl;
	private ScrollPane textureInputScrollPane;
	private VBox textureInputLayout;
	private TextField diffuseOutputPathControl;
	private TextField specularOutputPathControl;

	public Tooltip createTooltip(String text) {
		Tooltip tooltip = new Tooltip(text);

		tooltip.setFont(Font.font(12.0));
		tooltip.setMaxWidth(300.0);
		tooltip.setWrapText(true);
		tooltip.setStyle("-fx-background-color: rgb(95%, 95%, 95%); -fx-text-fill: rgb(0%, 0%, 0%)");

		return tooltip;
	}

	public boolean executableExists(String path) {
		try {
			Process process = Runtime.getRuntime().exec(path);
			process.waitFor();

			// It was able to run the executable without causing an exception,
			// so we can assume it exists.
			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public String findMakeTX() {
		// Try to find the maketx or txmake file

		// First check if it's right next to the jar or if maketx or txmake
		// is in %PATH%
		if (executableExists("maketx")) return "maketx";
		if (executableExists("txmake")) return "txmake";
		if (executableExists("maketx.exe")) return "maketx.exe";
		if (executableExists("txmake.exe")) return "txmake.exe";

		if (executableExists("./maketx")) return "./maketx";
		if (executableExists("./txmake")) return "./txmake";
		if (executableExists("./maketx.exe")) return "./maketx.exe";
		if (executableExists("./txmake.exe")) return "./txmake.exe";

		String RMAN_PREFIX = "C:\\Program Files\\Pixar\\RenderManProServer-";
		String RMAN_SUFFIX = "\\bin\\txmake.exe";
		for (int v1 = 20; v1 < 40; ++v1) {
			for (int v2 = 0; v2 < 10; ++v2) {
				String RMAN_PATH = RMAN_PREFIX + v1 + "." + v2 + RMAN_SUFFIX;
				if (new File(RMAN_PATH).exists()) return RMAN_PATH;
			}
		}

		String ARNOLD_PREFIX = "C:\\Program Files\\Autodesk\\Arnold\\maya";
		String ARNOLD_SUFFIX = "\\bin\\maketx.exe";
		for (int v1 = 2016; v1 < 2040; ++v1) {
			String ARNOLD_PATH = ARNOLD_PREFIX + v1 + ARNOLD_SUFFIX;
			if (new File(ARNOLD_PATH).exists()) return ARNOLD_PATH;
		}

		return "";
	}

	public File getFileChooserDirectory() {
		// Find a good directory to start the file chooser in.

		File dir = null;

		try {
			File tmpDir = new File(diffuseBaseValueControl.getText());
			if (tmpDir.exists()) dir = tmpDir.getParentFile();
		} catch (Exception ex) {
		}

		try {
			File tmpDir = new File(specularBaseValueControl.getText());
			if (tmpDir.exists()) dir = tmpDir.getParentFile();
		} catch (Exception ex) {
		}

		try {
			File tmpDir = new File(((TextField) textureInputLayout.lookup("tex_path")).getText());
			if (tmpDir.exists()) dir = tmpDir.getParentFile();
		} catch (Exception ex) {
		}

		return dir;
	}

	private static enum FileChooserType {
		EXECUTABLE, IMAGE, TEXTURE, ALL
	}

	public HBox addFileInput(final TextField textField, final boolean save, final String chooserTitle, final FileChooserType type, String tooltip) {
		HBox box = new HBox(2.0);

		box.getChildren().add(textField);

		textField.setPrefWidth(10000.0);
		textField.setTooltip(createTooltip(tooltip));

		Button fileChooserButton = new Button("...");
		fileChooserButton.setMaxWidth(32.0);
		fileChooserButton.setMinWidth(32.0);
		box.getChildren().add(fileChooserButton);

		fileChooserButton.setTooltip(createTooltip("Open File Dialog"));

		fileChooserButton.setOnAction((final ActionEvent e) -> {
			FileChooser fchooser = new FileChooser();
			fchooser.setTitle(chooserTitle);

			if (type == FileChooserType.EXECUTABLE) {
				fchooser.getExtensionFilters().add(new ExtensionFilter("All files", "*.*"));
				fchooser.getExtensionFilters().add(new ExtensionFilter("Executable", "*.exe"));
			} else if (type == FileChooserType.IMAGE) {
				fchooser.getExtensionFilters().add(new ExtensionFilter("Image files", "*.png", "*.jpg", "*.bmp"));
				fchooser.getExtensionFilters().add(new ExtensionFilter("PNG files", "*.png"));
				fchooser.getExtensionFilters().add(new ExtensionFilter("JPG files", "*.jpg"));
				fchooser.getExtensionFilters().add(new ExtensionFilter("BMP files", "*.bmp"));
			} else if (type == FileChooserType.TEXTURE) {
				fchooser.getExtensionFilters().add(new ExtensionFilter("Texture files", "*.tx", "*.tex"));
				fchooser.getExtensionFilters().add(new ExtensionFilter("Arnold/OIIO texture", "*.tx"));
				fchooser.getExtensionFilters().add(new ExtensionFilter("Renderman texture", "*.tex"));
				fchooser.getExtensionFilters().add(new ExtensionFilter("All files", "*.*"));

				if (rendererControl.getValue().equalsIgnoreCase("Arnold")) {
					fchooser.setSelectedExtensionFilter(fchooser.getExtensionFilters().get(1));
				} else if (rendererControl.getValue().equalsIgnoreCase("Renderman")) {
					fchooser.setSelectedExtensionFilter(fchooser.getExtensionFilters().get(2));
				}
			}

			try {
				File f = new File(textField.getText());
				if (f.exists()) {
					fchooser.setInitialDirectory(f.getParentFile());
				} else {
					fchooser.setInitialDirectory(getFileChooserDirectory());
				}
			} catch (Exception ex) {
				fchooser.setInitialDirectory(getFileChooserDirectory());
			}

			File f = null;
			if (save)
				f = fchooser.showSaveDialog(MainWindow.stage);
			else f = fchooser.showOpenDialog(MainWindow.stage);

			if (f != null) {
				textField.setText(f.getAbsolutePath());
			}
		});

		return box;
	}

	public TextField createNumberInput(double initialValue) {
		TextField field = new TextField(Double.toString(initialValue));

		DecimalFormat df = new DecimalFormat("#.#");
		df.setMinimumFractionDigits(1);
		df.setMaximumFractionDigits(16);

		field.textProperty().addListener(new ChangeListener<String>() {

			@Override
			public void changed(ObservableValue<? extends String> arg0, String oldValue, String newValue) {
				newValue = newValue.replaceAll(" ", "").replaceAll("\t", "");
				if (newValue.equalsIgnoreCase("-")) return; // The user is trying to type a negative number, so allow them
				if (newValue.isEmpty()) return; // They want to clear the input in order to write something else in, so allow them

				try {
					Double.valueOf(newValue).doubleValue();
				} catch (Exception ex) {
					try {
						double val = Double.valueOf(oldValue).doubleValue();
						field.setText(df.format(val));
					} catch (Exception ex2) {
						field.setText("");
					}
				}
			}

		});

		field.focusedProperty().addListener(new ChangeListener<Boolean>() {

			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				double val = 0.0;
				try {
					val = Double.valueOf(field.getText()).doubleValue();
				} catch (Exception ex) {
				}

				field.setText(df.format(val));
			}

		});

		return field;
	}

	public void addController(String label, Region control, Pane parent, String tooltip) {
		HBox hbox = new HBox(4.0);

		Label labelControl = new Label(label);
		labelControl.setMinWidth(200.0);
		labelControl.setMaxWidth(200.0);
		labelControl.setTooltip(createTooltip(tooltip));
		hbox.getChildren().add(labelControl);
		hbox.getChildren().add(control);

		control.setPrefWidth(10000.0);
		if (control instanceof Control) {
			((Control) control).setTooltip(createTooltip(tooltip));
		}

		parent.getChildren().add(hbox);
	}

	public void addGlobalSettings() {
		rendererControl = new ChoiceBox<String>(FXCollections.observableArrayList("Arnold", "Renderman"));
		rendererControl.setValue("Arnold");
		addController("Render Engine:", rendererControl, root, DOC_RENDERER);

		maketxPathControl = new TextField(findMakeTX());
		addController("MakeTX/TXmake path:", addFileInput(maketxPathControl, false, "MakeTX/TXmake path", FileChooserType.EXECUTABLE, DOC_MAKETX_PATH), root,
				DOC_MAKETX_PATH);

		outputModeControl = new ChoiceBox<String>(FXCollections.observableArrayList("TX/TEX", "Individual MIPMAP levels"));
		outputModeControl.setValue("TX/TEX");
		addController("Output Mode:", outputModeControl, root, DOC_OUTPUTMODE);
	}

	public void addTextureInputBox() {
		final HBox hbox = new HBox();
		hbox.setSpacing(16.0);
		hbox.setPadding(new Insets(8.0));
		hbox.setBorder(new Border(new BorderStroke(Color.LIGHTGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));

		TextField pathControl = new TextField();
		pathControl.setId("tex_path");
		hbox.getChildren().add(addFileInput(pathControl, false, "Bump/Normal/Displacement map", FileChooserType.IMAGE, DOC_TEX_PATH));

		HBox scaleHBox = new HBox();
		Label scaleLabel = new Label("Scale:");
		scaleLabel.setMinWidth(36.0);
		TextField scaleControl = createNumberInput(1.0);
		scaleControl.setId("tex_scale");
		scaleControl.setMinWidth(60.0);
		scaleHBox.getChildren().addAll(scaleLabel, scaleControl);
		hbox.getChildren().add(scaleHBox);
		scaleLabel.setTooltip(createTooltip(DOC_TEX_SCALE));
		scaleControl.setTooltip(createTooltip(DOC_TEX_SCALE));

		HBox typeHBox = new HBox();
		Label typeLabel = new Label("Type:");
		typeLabel.setMinWidth(36.0);
		ChoiceBox<String> typeControl = new ChoiceBox<>(FXCollections.observableArrayList("Bump", "Normal", "Displacement"));
		typeControl.setId("tex_type");
		typeControl.setMinWidth(100.0);
		typeControl.setValue("Bump");
		typeHBox.getChildren().addAll(typeLabel, typeControl);
		hbox.getChildren().add(typeHBox);
		typeLabel.setTooltip(createTooltip(DOC_TEX_TYPE));
		typeControl.setTooltip(createTooltip(DOC_TEX_TYPE));

		Button deleteButton = new Button("X");
		deleteButton.setMinWidth(24.0);
		deleteButton.setMaxWidth(24.0);
		hbox.getChildren().add(deleteButton);
		deleteButton.setOnAction((final ActionEvent e) -> {
			textureInputLayout.getChildren().remove(hbox);

			if (textureInputLayout.getChildren().size() == 0) addTextureInputBox();
		});
		deleteButton.setTooltip(createTooltip("Remove map"));

		textureInputLayout.getChildren().add(hbox);
	}

	public void addTextureInput() {
		Label label = new Label("Bump/Normal/Displacement maps:");
		root.getChildren().add(label);

		textureInputLayout = new VBox();

		textureInputScrollPane = new ScrollPane(textureInputLayout);
		textureInputScrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
		textureInputScrollPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
		textureInputScrollPane.setPrefWidth(10000.0);
		textureInputScrollPane.setPrefHeight(10000.0);

		textureInputLayout.maxWidthProperty().bind(textureInputScrollPane.widthProperty().subtract(14.0));

		addTextureInputBox();

		root.getChildren().add(textureInputScrollPane);

		Button addButton = new Button("+");
		addButton.setPrefWidth(10000.0);
		addButton.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.EXTRA_BOLD, 20.0));
		addButton.setMinHeight(24.0);
		addButton.setMaxHeight(24.0);
		addButton.setPadding(new Insets(-4));
		root.getChildren().add(addButton);
		addButton.setOnAction((final ActionEvent e) -> {
			addTextureInputBox();
		});
		addButton.setTooltip(createTooltip("Add map"));
	}

	public void addTextureSettings() {
		unitSizeControl = createNumberInput(1.0);
		addController("UV Unit World Size:", unitSizeControl, root, DOC_UNITSIZE);

		generateDiffuseControl = new CheckBox();
		generateDiffuseControl.setSelected(true);
		addController("Generate Diffuse Roughness:", generateDiffuseControl, root, DOC_GENERATE_DIFFUSE);
		diffuseBaseValueControl = new TextField();
		diffuseBaseValueControl.setText("0.0");
		addController("Diffuse Roughness Value/Texture:",
				addFileInput(diffuseBaseValueControl, false, "Diffuse Roughness Texture", FileChooserType.IMAGE, DOC_DIFFUSE_VALUE), root, DOC_DIFFUSE_VALUE);

		generateSpecularControl = new CheckBox();
		generateSpecularControl.setSelected(true);
		addController("Generate Specular Roughness:", generateSpecularControl, root, DOC_GENERATE_SPECULAR);
		specularBaseValueControl = new TextField();
		specularBaseValueControl.setText("0.0");
		addController("Specular Roughness Value/Texture:",
				addFileInput(specularBaseValueControl, false, "Specular Roughness Texture", FileChooserType.IMAGE, DOC_SPECULAR_VALUE), root,
				DOC_SPECULAR_VALUE);

		addTextureInput();
	}

	public void addOutputSettings() {
		diffuseOutputPathControl = new TextField();
		addController("Diffuse Output path:",
				addFileInput(diffuseOutputPathControl, true, "Save as: Diffuse Roughness Texture", FileChooserType.TEXTURE, DOC_DIFFUSE_OUTPUT), root,
				DOC_DIFFUSE_OUTPUT);
		specularOutputPathControl = new TextField();
		addController("Specular Output path:",
				addFileInput(specularOutputPathControl, true, "Save as: Specular Roughness Texture", FileChooserType.TEXTURE, DOC_SPECULAR_OUTPUT), root,
				DOC_SPECULAR_OUTPUT);

		Button exportButton = new Button("Generate Textures");
		exportButton.setPrefWidth(10000.0);
		exportButton.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.EXTRA_BOLD, 16.0));
		exportButton.setMinHeight(32.0);
		exportButton.setMaxHeight(32.0);
		exportButton.setPadding(new Insets(0));
		root.getChildren().add(exportButton);
		exportButton.setOnAction((final ActionEvent e) -> {
			exportButtonClicked();
		});
	}

	public void exportButtonClicked() {
		showProgressDialog();

		try {

			// To prevent the UI from stalling, do the exporting on a separate thread.
			new Thread(new Runnable() {

				@Override
				public void run() {

					try {

						Settings settings = new Settings();

						settings.renderer = RENDERER.ARNOLD;
						if (rendererControl.getValue().equalsIgnoreCase("Renderman")) settings.renderer = RENDERER.RENDERMAN;

						settings.outputMode = OUTPUTMODE.INDIVIDUAL_LEVELS;

						if (outputModeControl.getValue().equalsIgnoreCase("TX/TEX")) {
							settings.outputMode = OUTPUTMODE.TEXTURE;

							if (!executableExists(maketxPathControl.getText())) {
								showError("Cannot find maketx or txmake");
								return;
							}
							settings.maketxPath = maketxPathControl.getText();
						}

						try {
							settings.unitSize = (float) Double.parseDouble(unitSizeControl.getText());
						} catch (Exception ex) {
							ex.printStackTrace();
							showError("Cannot set UV Unit World Size");
							return;
						}

						for (Node item : textureInputLayout.getChildren()) {
							if (item instanceof HBox) {
								HBox hbox = (HBox) item;

								Node texPath = ((Parent)hbox.getChildren().get(0)).getChildrenUnmodifiable().get(0);
								Node texScale = ((Parent)hbox.getChildren().get(1)).getChildrenUnmodifiable().get(1);
								Node texType = ((Parent)hbox.getChildren().get(2)).getChildrenUnmodifiable().get(1);
								
								File path = new File(((TextField) texPath).getText());
								if (!path.exists()) {
									showError("Cannot find given texture: " + path.getPath());
									return;
								}

								double scale = 0.0;
								try {
									scale = Double.parseDouble(((TextField) texScale).getText());
								} catch (Exception ex) {
									ex.printStackTrace();
									showError("Cannot get scale for texture: " + path.getPath());
									return;
								}

								@SuppressWarnings("unchecked")
								String type = ((ChoiceBox<String>) texType).getValue();
								MAPTYPE mtype = MAPTYPE.BUMP;
								if (type.equalsIgnoreCase("Normal")) mtype = MAPTYPE.NORMAL;
								if (type.equalsIgnoreCase("Displacement")) mtype = MAPTYPE.DISPLACEMENT;

								try {
									settings.imgs.add(new ImageContainer(path));
									settings.normalisationFactors.add((float) scale);
									settings.mapType.add(mtype);
								} catch (Exception ex) {
									ex.printStackTrace();
									showError("Could not load texture: " + path.getPath());
									return;
								}
							}
						}

						if (settings.imgs.size() == 0) {
							showError("No textures given");
							return;
						}

						settings.callback = new ProgressCallback() {

							@Override
							public void onProgress(double progress, String status) {
								setProgress(progress, status);
							}

							@Override
							public void addProgress(double additionalProgress) {
								addProgressDialog(additionalProgress);
							}

						};

						ImageContainer diffuseImg = null;
						float diffuseValue = 0.0f;
						String diffuseOutputPath = "";

						// Verify and parse the settings first
						if (generateDiffuseControl.isSelected()) {
							try {
								// Try to set it as a float. If it isn't a number, it must be a file path
								// and we will know that since it will generate an exception.
								diffuseValue = Float.parseFloat(diffuseBaseValueControl.getText());
							} catch (Exception ex) {
								// Must be a file path.
								File f = new File(diffuseBaseValueControl.getText());
								if (!f.exists()) {
									showError("Diffuse Roughness Value/Texture is either not a number or not an existing file path");
									return;
								}
								try {
									diffuseImg = new ImageContainer(f);
								} catch (Exception ex2) {
									ex2.printStackTrace();
									showError("Diffuse Roughness Value/Texture is either not a number or not an existing file path");
									return;
								}
							}

							if (diffuseOutputPathControl.getText().trim().isEmpty()) {
								showError("Diffuse Output path is not a proper path");
								return;
							}

							try {
								if (new File(diffuseOutputPathControl.getText()).getName().isEmpty()) {
									showError("Diffuse Output path is not a proper path");
									return;
								}
							} catch (Exception ex) {
								ex.printStackTrace();
								showError("Diffuse Output path is not a proper path");
								return;
							}
							
							diffuseOutputPath = diffuseOutputPathControl.getText();
						}

						ImageContainer specularImg = null;
						float specularValue = 0.0f;
						String specularOutputPath = "";

						// Verify and parse the settings first
						if (generateSpecularControl.isSelected()) {
							try {
								// Try to set it as a float. If it isn't a number, it must be a file path
								// and we will know that since it will generate an exception.
								specularValue = Float.parseFloat(specularBaseValueControl.getText());
							} catch (Exception ex) {
								// Must be a file path.
								File f = new File(specularBaseValueControl.getText());
								if (!f.exists()) {
									showError("Specular Roughness Value/Texture is either not a number or not an existing file path");
									return;
								}
								try {
									specularImg = new ImageContainer(f);
								} catch (Exception ex2) {
									ex2.printStackTrace();
									showError("Specular Roughness Value/Texture is either not a number or not an existing file path");
									return;
								}
							}

							if (specularOutputPathControl.getText().trim().isEmpty()) {
								showError("Specular Output path is not a proper path");
								return;
							}

							try {
								if (new File(specularOutputPathControl.getText()).getName().isEmpty()) {
									showError("Specular Output path is not a proper path");
									return;
								}
							} catch (Exception ex) {
								ex.printStackTrace();
								showError("Specular Output path is not a proper path");
								return;
							}
							
							specularOutputPath = specularOutputPathControl.getText();
						}

						// Generate diffuse roughness texture
						if (generateDiffuseControl.isSelected()) {
							setProgressTitle("Generating Diffuse Roughness Texture...");

							settings.roughnessImg = diffuseImg;
							settings.roughnessValue = diffuseValue;
							settings.outputPath = diffuseOutputPath;

							try {
								Bump2Roughness solver = new Bump2Roughness(settings);

								solver.calculateRoughness();

								solver.writeOutput();
							} catch (Exception ex) {
								ex.printStackTrace();
								showError("Could not generate diffuse roughness texture");
								return;
							}
						}

						// Generate specular roughness texture
						if (generateSpecularControl.isSelected()) {
							setProgressTitle("Generating Specular Roughness Texture...");

							settings.roughnessImg = specularImg;
							settings.roughnessValue = specularValue;
							settings.outputPath = specularOutputPath;

							try {
								Bump2Roughness solver = new Bump2Roughness(settings);

								solver.calculateRoughness();

								solver.writeOutput();
							} catch (Exception ex) {
								ex.printStackTrace();
								showError("Could not generate specular roughness texture");
								return;
							}
						}

						showDoneDialog();

					} catch (Exception ex) {
						ex.printStackTrace();
						showError("Couldn't generate roughness textures");
						return;
					}

				}

			}).start();

		} catch (Exception ex) {
			closeProgressDialog();
		}
	}

	private Stage progressStage;
	private Label progressLabel;
	private ProgressBar progressBar;

	public void setupProgressDialog() {
		progressStage = new Stage();
		progressStage.initOwner(stage);
		progressStage.setTitle("Generating textures...");
		progressStage.initStyle(StageStyle.UTILITY);
		progressStage.setResizable(false);
		progressStage.initModality(Modality.APPLICATION_MODAL);

		progressLabel = new Label("Generating textures...");
		progressLabel.setPrefWidth(10000.0);

		progressBar = new ProgressBar();
		progressBar.setProgress(-1.0);
		progressBar.setPrefWidth(10000.0);

		VBox layout = new VBox();
		layout.setAlignment(Pos.CENTER);
		layout.setPadding(new Insets(10.0));
		layout.setSpacing(10.0);
		layout.getChildren().addAll(progressLabel, progressBar);

		Scene scene = new Scene(layout, 400, 80);

		progressStage.setScene(scene);
	}

	public void setProgress(double progress, String status) {
		Platform.runLater(() -> {
			progressBar.setProgress(progress);
			progressLabel.setText(status);
		});
	}
	
	public void addProgressDialog(double additionalProgress) {
		Platform.runLater(() -> {
			progressBar.setProgress(progressBar.getProgress() + additionalProgress);
		});
	}

	public void showProgressDialog() {
		Platform.runLater(() -> {
			progressBar.setProgress(-1);
			progressLabel.setText("Generating textures...");
			
			stage.getScene().getRoot().setDisable(true);
			progressStage.show();
		});
	}

	public void closeProgressDialog() {
		Platform.runLater(() -> {
			stage.getScene().getRoot().setDisable(false);
			progressStage.hide();
		});
	}

	public void setProgressTitle(String title) {
		Platform.runLater(() -> {
			progressStage.setTitle(title);
		});
	}

	public void showError(String text) {
		Platform.runLater(() -> {
			final Stage dialogStage = new Stage();
			dialogStage.initOwner(stage);
			dialogStage.setTitle("Error!");
			dialogStage.initStyle(StageStyle.UTILITY);
			dialogStage.setResizable(false);
			dialogStage.initModality(Modality.APPLICATION_MODAL);

			Label errorLabel = new Label(text);
			errorLabel.setPrefWidth(10000.0);

			Button okButton = new Button("Close");

			okButton.setOnAction((ActionEvent e) -> {
				dialogStage.close();
			});

			VBox layout = new VBox();
			layout.setAlignment(Pos.CENTER);
			layout.setPadding(new Insets(10.0));
			layout.setSpacing(10.0);
			layout.getChildren().addAll(errorLabel, okButton);

			Scene scene = new Scene(layout, 400, 80);

			dialogStage.setScene(scene);

			dialogStage.showAndWait();

			closeProgressDialog();
		});
	}
	
	public void showDoneDialog() {
		Platform.runLater(() -> {
			final Stage dialogStage = new Stage();
			dialogStage.initOwner(stage);
			dialogStage.setTitle("Done!");
			dialogStage.initStyle(StageStyle.UTILITY);
			dialogStage.setResizable(false);
			dialogStage.initModality(Modality.APPLICATION_MODAL);

			Label errorLabel = new Label("The textures have successfully been generated!");
			errorLabel.setPrefWidth(10000.0);

			Button okButton = new Button("Ok");

			okButton.setOnAction((ActionEvent e) -> {
				dialogStage.close();
			});

			VBox layout = new VBox();
			layout.setAlignment(Pos.CENTER);
			layout.setPadding(new Insets(10.0));
			layout.setSpacing(10.0);
			layout.getChildren().addAll(errorLabel, okButton);

			Scene scene = new Scene(layout, 300, 80);

			dialogStage.setScene(scene);

			dialogStage.showAndWait();

			closeProgressDialog();
		});
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		MainWindow.stage = primaryStage;

		root = new VBox();
		root.setPadding(new Insets(10));
		root.setSpacing(8);

		addGlobalSettings();
		root.getChildren().add(new Separator());
		addTextureSettings();
		root.getChildren().add(new Separator());
		addOutputSettings();

		setupProgressDialog();

		Scene scene = new Scene(root, 720, 720);

		primaryStage.setTitle("Bump2Roughness");
		primaryStage.setScene(scene);
		primaryStage.show();
	}

}
