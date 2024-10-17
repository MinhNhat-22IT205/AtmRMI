import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIClient {
    private static UserService userService;

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            userService = (UserService) registry.lookup("UserService");

            JFrame frame = new JFrame("Đăng nhập / Đăng ký");
            frame.setSize(400, 300);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new CardLayout());

            JPanel loginPanel = new JPanel(new GridLayout(3, 2));
            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();
            JButton loginButton = new JButton("Đăng nhập");
            JButton registerButton = new JButton("Đăng ký");

            loginPanel.add(new JLabel("Tên người dùng:"));
            loginPanel.add(usernameField);
            loginPanel.add(new JLabel("Mật khẩu:"));
            loginPanel.add(passwordField);
            loginPanel.add(loginButton);
            loginPanel.add(registerButton);

            JPanel welcomePanel = new JPanel();
            JLabel welcomeLabel = new JLabel();
            welcomePanel.add(welcomeLabel);

            frame.add(loginPanel, "login");
            frame.add(welcomePanel, "welcome");

            CardLayout cl = (CardLayout) frame.getContentPane().getLayout();

            loginButton.addActionListener(e -> {
                try {
                    String username = usernameField.getText();
                    String password = new String(passwordField.getPassword());
                    if (userService.login(username, password)) {
                        welcomeLabel.setText("Chào mừng, " + username + "!");
                        cl.show(frame.getContentPane(), "welcome");
                    } else {
                        JOptionPane.showMessageDialog(frame, "Đăng nhập thất bại!");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            registerButton.addActionListener(e -> {
                JTextField newUsername = new JTextField();
                JPasswordField newPassword = new JPasswordField();
                JPasswordField confirmPassword = new JPasswordField();

                Object[] fields = {
                        "Tên người dùng:", newUsername,
                        "Mật khẩu:", newPassword,
                        "Xác nhận mật khẩu:", confirmPassword
                };

                int option = JOptionPane.showConfirmDialog(
                        frame, fields, "Đăng ký tài khoản", JOptionPane.OK_CANCEL_OPTION);

                if (option == JOptionPane.OK_OPTION) {
                    String username = newUsername.getText();
                    String password = new String(newPassword.getPassword());
                    String confirm = new String(confirmPassword.getPassword());

                    if (!password.equals(confirm)) {
                        JOptionPane.showMessageDialog(frame, "Mật khẩu không khớp!");
                        return;
                    }

                    try {
                        if (userService.register(username, password)) {
                            JOptionPane.showMessageDialog(frame, "Đăng ký thành công!");
                        } else {
                            JOptionPane.showMessageDialog(frame, "Đăng ký thất bại!");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            frame.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
