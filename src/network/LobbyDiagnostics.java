import java.io.*;
import java.net.*;
import java.nio.file.*;

/** Keep handshake/worker failures readable and preserve their causes for diagnosis. */
final class LobbyDiagnostics {
    static Throwable cause(Throwable failure){while((failure instanceof java.util.concurrent.ExecutionException||failure instanceof java.util.concurrent.CompletionException)&&failure.getCause()!=null)failure=failure.getCause();return failure;}
    static String describe(Throwable failure){
        failure=cause(failure);
        if(failure instanceof EOFException)return "The connection closed before the lobby response completed. Reload Game on both devices and check build/logs/lobby.log on the host.";
        if(failure instanceof BindException)return "Cannot open the host port. Another lobby or application may already be using it; try a different port.";
        if(failure instanceof UnknownHostException)return "Host address was not found. Enter the host's local IPv4 address.";
        if(failure instanceof ConnectException)return "Could not reach the host. Check its IP/port and that Host Lobby is running.";
        if(failure instanceof SocketTimeoutException)return "The lobby connection timed out. Check the host, IP/port and network access.";
        if(failure instanceof LinkageError)return "Game files changed or do not match. Use Reload Game on every device.";
        String message=failure.getMessage();return failure.getClass().getSimpleName()+(message==null||message.trim().isEmpty()?" (no message; see build/logs/lobby.log)":": "+message);
    }
    static synchronized void log(String context,Throwable failure){
        StringWriter details=new StringWriter();PrintWriter writer=new PrintWriter(details);writer.println(java.time.Instant.now()+" "+context);failure.printStackTrace(writer);writer.flush();
        System.err.print(details);
        try{Path path=Paths.get("build","logs","lobby.log");Files.createDirectories(path.getParent());Files.writeString(path,details.toString(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(IOException ignored){}
    }
}
