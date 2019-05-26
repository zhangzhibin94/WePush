package com.fangxuele.tool.push.ui.listener;

import com.fangxuele.tool.push.App;
import com.fangxuele.tool.push.ui.form.MainWindow;
import com.fangxuele.tool.push.ui.form.MessageEditForm;
import com.fangxuele.tool.push.ui.form.PushForm;
import com.fangxuele.tool.push.ui.form.PushHisForm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import static com.fangxuele.tool.push.ui.form.MainWindow.mainWindow;
import static com.fangxuele.tool.push.ui.form.PushForm.pushForm;

/**
 * <pre>
 * tab事件监听
 * </pre>
 *
 * @author <a href="https://github.com/rememberber">RememBerBer</a>
 * @since 2017/6/21.
 */
public class TabListener {

    private static boolean warnFlag = true;

    private static boolean windowMax;

    private static Point origin = new Point();

    private static Rectangle desktopBounds;
    private static Rectangle normalBounds;

    static {
        initBounds();
    }

    public static void addListeners() {
        MainWindow.mainWindow.getTabbedPane().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                int index = MainWindow.mainWindow.getTabbedPane().getSelectedIndex();
                switch (index) {
                    case 6:
                        PushHisForm.init();
                        break;
                    case 3:
                        if (warnFlag) {
                            JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "\n请确认您了解所要发送消息类型的使用频率、使用规范和限制规则，\n" +
                                            "以免账号相关功能被封禁等给您带来麻烦", "提示",
                                    JOptionPane.INFORMATION_MESSAGE);
                            warnFlag = false;
                        }
                        break;
                    case 4:
                        PushForm.pushForm.getPushMsgName().setText(MessageEditForm.messageEditForm.getMsgNameField().getText());
                        PushListener.refreshPushInfo();
                        break;
                    default:
                        break;
                }
            }
        });

        MainWindow.mainWindow.getTitlePanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 当鼠标按下的时候获得窗口当前的位置
                origin.x = e.getX();
                origin.y = e.getY();

                super.mousePressed(e);
            }

        });

        MainWindow.mainWindow.getTitlePanel().addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (windowMax) {
                    App.mainFrame.setBounds(normalBounds);
//            maxLabel.setIcon(maxIcon);
                    windowMax = false;
                }

                // 当鼠标拖动时获取窗口当前位置
                Point p = App.mainFrame.getLocation();
                // 设置窗口的位置
                App.mainFrame.setLocation(p.x + e.getX() - origin.x, p.y + e.getY()
                        - origin.y);
                super.mouseDragged(e);
            }
        });

        MainWindow.mainWindow.getWindowsMinLabel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                App.mainFrame.setExtendedState(JFrame.ICONIFIED);
                super.mouseClicked(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
            }
        });

        MainWindow.mainWindow.getWindowsMaxLabel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                maxOrRestoreWindow();
                super.mouseClicked(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
            }
        });

        MainWindow.mainWindow.getWindowsCloseLabel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!pushForm.getPushStartButton().isEnabled()) {
                    JOptionPane.showMessageDialog(mainWindow.getPushPanel(),
                            "有推送任务正在进行！\n\n为避免数据丢失，请先停止!\n\n", "Sorry~",
                            JOptionPane.WARNING_MESSAGE);
                } else {
                    App.sqlSession.close();
                    System.exit(0);
                }
                super.mouseClicked(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
            }
        });

    }

    private static void initBounds() {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension screenSize = tk.getScreenSize();
        //上面这种方式获取的是整个显示屏幕的大小，包含了任务栏的高度。
        Insets screenInsets = Toolkit.getDefaultToolkit()
                .getScreenInsets(App.mainFrame.getGraphicsConfiguration());
        desktopBounds = new Rectangle(
                screenInsets.left, screenInsets.top,
                screenSize.width - screenInsets.left - screenInsets.right,
                screenSize.height - screenInsets.top - screenInsets.bottom);

        normalBounds = new Rectangle(
                (int) (screenSize.width * 0.1), (int) (screenSize.height * 0.06), (int) (screenSize.width * 0.8),
                (int) (screenSize.height * 0.83));

    }

    private static void maxOrRestoreWindow() {
        if (windowMax) {
            App.mainFrame.setBounds(normalBounds);
//            maxLabel.setIcon(maxIcon);
            windowMax = false;
        } else {
            App.mainFrame.setBounds(desktopBounds);
//            maxLabel.setIcon(restoreIcon);
            windowMax = true;
        }
    }
}
