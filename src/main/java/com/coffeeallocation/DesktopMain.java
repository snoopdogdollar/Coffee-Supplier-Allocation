package com.coffeeallocation;

import com.coffeeallocation.view.DesktopFrame;
import com.coffeeallocation.view.UiKit;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Locale;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;

/** Desktop entry point. Main remains the original console application. */
public final class DesktopMain {
    private DesktopMain() {}

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Cần môi trường desktop để mở giao diện Swing.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                var keys = UIManager.getDefaults().keys();
                while (keys.hasMoreElements()) {
                    Object key = keys.nextElement();
                    if (UIManager.get(key) instanceof FontUIResource) {
                        UIManager.put(key, new FontUIResource("Segoe UI", Font.PLAIN, 14));
                    }
                }
                JComponent.setDefaultLocale(Locale.forLanguageTag("vi-VN"));
                new DesktopFrame().setVisible(true);
            } catch (Exception exception) {
                UiKit.error(new JPanel(), exception);
            }
        });
    }
}
