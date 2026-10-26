package edu.campus.circulation;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Desktop entry point. Business modules will be added in later iterations. */
public final class App {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    private App() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.severe("需要图形桌面环境，请在桌面会话中启动应用。");
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(App::showWindow);
    }

    private static void showWindow() {
        JFrame frame = new JFrame("低碳校园资源循环流转桌面系统");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JLabel welcome = new JLabel(
                "<html><div style='text-align:center'>"
                        + "<h1>低碳校园资源循环流转</h1>"
                        + "<p>项目骨架已就绪，业务功能待开发。</p>"
                        + "<p>账号 · 物品 · 流转 · 评价 · 统计 · 管理</p>"
                        + "</div></html>",
                SwingConstants.CENTER);
        welcome.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        frame.add(welcome, BorderLayout.CENTER);
        frame.setSize(760, 480);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        LOGGER.info("桌面应用启动完成。");
    }
}
