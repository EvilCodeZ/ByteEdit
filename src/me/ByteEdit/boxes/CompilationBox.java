package me.ByteEdit.boxes;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import me.ByteEdit.edit.Disassembler;
import me.ByteEdit.main.Main;

public class CompilationBox extends JFrame {

	private JPanel contentPane;
	private JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
	RSyntaxTextArea textArea = new RSyntaxTextArea();
	CompilationSuccess compSuccess;

	public CompilationBox() {
		compSuccess = new CompilationSuccess();
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		setTitle("Java Compiler");
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setBounds((int) (Toolkit.getDefaultToolkit().getScreenSize().getWidth() / 2 - 400),
				(int) (Toolkit.getDefaultToolkit().getScreenSize().getHeight() / 2 - 200), 800, 400);
		contentPane = new JPanel();
		contentPane.setBackground(Color.GRAY);
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));

		Main.theme.apply(textArea);
		textArea.setEditable(true);
		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
		textArea.setCodeFoldingEnabled(true);
		textArea.setBackground(Color.LIGHT_GRAY);

		RTextScrollPane scrollPane = new RTextScrollPane();
		scrollPane.setViewportView(textArea);
		scrollPane.setLineNumbersEnabled(true);
		scrollPane.setFoldIndicatorEnabled(true);
		scrollPane.getGutter().setBackground(Color.LIGHT_GRAY);
		contentPane.add(scrollPane);

		KeyStroke ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK);
		textArea.registerKeyboardAction(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (compiler == null) {
					Main.showError("No Java Compiler found!\nYou need JDK installed!");
					return;
				}
				try (StringWriter out = new StringWriter(); PrintWriter outWriter = new PrintWriter(out)) {
					File tmpFolder = Files.createTempDirectory("compiled").toFile();

					String source = textArea.getText();
					if (!source.contains("\npublic class Compiled {\n")) {
						int importIndex = source.lastIndexOf("\nimport");
						if (importIndex == -1) {
							if (source.startsWith("import")) {
								importIndex = 0;
							}
						} else {
							importIndex++;
						}
						if (importIndex != -1) {
							int newLineIndex = source.indexOf('\n', importIndex);
							source = source.substring(0, newLineIndex) + "\npublic class Compiled {\n"
									+ source.substring(newLineIndex) + "\n}";
						} else {
							source = "public class Compiled {\n" + source + "\n}";
						}
					}

					compiler.getTask(outWriter, null, null, Arrays.asList("-d", tmpFolder.getAbsolutePath()), null,
							Arrays.asList(new JavaSourceFromString("Compiled", source))).call();
					String res = out.toString();
					File clazz = new File(tmpFolder, "Compiled.class");
					if (res.isEmpty()) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						InputStream is = new FileInputStream(clazz);
						byte[] tmp = new byte[1024];
						int r;
						while ((r = is.read(tmp)) > 0) {
							baos.write(tmp, 0, r);
						}
						is.close();
						ClassReader read = new ClassReader(baos.toByteArray());
						ClassNode node = new ClassNode();
						read.accept(node, 0);
						compSuccess.textArea.setText(Disassembler.disassemble(node));
						compSuccess.setVisible(true);
					} else {
						Main.showError(res);
					}
					if (clazz.exists())
						Files.delete(clazz.toPath());
					Files.delete(tmpFolder.toPath());
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}, ctrlS, JComponent.WHEN_FOCUSED);
	}

	class JavaSourceFromString extends SimpleJavaFileObject {
		final String code;

		public JavaSourceFromString(String name, String code) {
			super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.code = code;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return code;
		}
	}
}
