package hack.web.dto;

/**
 * Body of {@code POST /api/v1/command} — a single game command string
 * (movement key, inventory letter, etc.), interpreted by {@code GameEngine}.
 */
public class CommandRequest {

    private String cmd;

    public CommandRequest() { }

    public CommandRequest(String cmd) {
        this.cmd = cmd;
    }

    public String getCmd()            { return cmd; }
    public void   setCmd(String cmd)  { this.cmd = cmd; }
}
