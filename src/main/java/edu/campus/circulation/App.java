package edu.campus.circulation;

import edu.campus.circulation.repository.DemoDatabase;
import edu.campus.circulation.service.DemoService;
import edu.campus.circulation.ui.DemoWindow;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class App {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    private App() {}

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.severe("需要图形桌面环境，请在桌面会话中启动应用。");
            System.exit(1);
            return;
        }
        Path file = Path.of(System.getProperty("campus.demo.db", "data/demo.db")).toAbsolutePath();
        try {
            DemoService service = new DemoService(new DemoDatabase(file));
            SwingUtilities.invokeLater(() -> new DemoWindow(service, file).setVisible(true));
        } catch (IOException | SQLException failure) {
            LOGGER.log(Level.SEVERE, "演示数据库初始化失败", failure);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "无法打开演示数据库。请检查文件权限和数据库版本。\n" + file,
                    "启动失败", JOptionPane.ERROR_MESSAGE));
        }
    }
}
