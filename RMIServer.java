import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

interface UserService extends Remote {
    boolean register(String username, String password) throws RemoteException;
    boolean login(String username, String password) throws RemoteException;
}

class UserServiceImpl extends UnicastRemoteObject implements UserService {
    private Connection connection;

    public UserServiceImpl() throws RemoteException {
        try {
            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/bank", "root", "");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized boolean register(String username, String password) throws RemoteException {
        try {
            String query = "INSERT INTO user (username, pass) VALUES (?, ?)";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean login(String username, String password) throws RemoteException {
        try {
            String query = "SELECT * FROM user WHERE username = ? AND pass = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}

public class RMIServer {
    public static void main(String[] args) {
        try {
            UserService userService = new UserServiceImpl();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("UserService", userService);
            System.out.println("RMI Server is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
