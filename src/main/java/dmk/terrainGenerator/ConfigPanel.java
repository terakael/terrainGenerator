package dmk.terrainGenerator;

import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class ConfigPanel extends JPanel {
	private final MyPanel mainPanel;
	private Map<JTextField, Consumer<String>> textFields = new HashMap<>();
	public ConfigPanel(MyPanel mainPanel) {
		this.mainPanel = mainPanel;
		
		addButton("color", e -> mainPanel.toggleDrawColor());
		addButton("redraw", e -> {
			textFields.forEach((field, setter) -> setter.accept(field.getText())); 
			mainPanel.redraw();
		});
		
		addTextbox("scale", mainPanel::getScaleAsString, mainPanel::setScaleAsString);
	}
	
	private void addButton(String name, ActionListener l) {
		JButton btn = new JButton(name);
		btn.addActionListener(l);
		this.add(btn);
	}
	
	private void addTextbox(String name, Supplier<String> getter, Consumer<String> setter) {
		this.add(new JLabel(name + ":"));
		
		JTextField textField = new JTextField(getter.get());
		textFields.put(textField, setter);
		
		this.add(textField, -1);
	}
}
