import java.io.*;
import java.net.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;

/** Authoritative simulation and real loopback socket integration checks. */
public class LobbyCheck {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-6, message + ": " + actual);
    }
    static void waitFor(BooleanSupplier ready, String message) throws Exception {
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out: " + message);
            Thread.sleep(10);
        }
    }
    static void tick(LobbyWorld world, double seconds) {
        for (int i = 0; i < Math.round(seconds / .01); i++) world.tick(.01);
    }
    static void position(LobbyWorld.Player player, double x, double z) {
        player.x = player.body.x = x; player.z = player.body.z = z;
        player.y = player.body.lift = player.verticalSpeed = 0;
        player.body.vx = player.body.vz = 0;
    }
    static void simulation() {
        LobbyWorld world = new LobbyWorld();
        check(world.arena.dummies.isEmpty(),"Waiting room has no active map targets");
        LobbyWorld.Player a = world.add("Host", true), b = world.add("Guest", false);
        world.action(b.id, 4, ""); check(!world.started, "Only host can start");
        world.input(a.id, 1, 0, 0, false); tick(world, .1);
        near(a.x, -6, "Waiting room freezes movement");
        world.action(a.id, 4, ""); tick(world, .1); near(a.x, -5.6, "Server movement speed");
        world.action(a.id, 3, "Gauntlet"); near(a.combat.health, 115, "Server equips Gauntlet");
        world.action(b.id, 3, "Gauntlet");
        world.input(a.id, 0, 0, 0, false);
        position(a, 0, 0); position(b, 0, -1.4);
        world.action(a.id, 0, "PUNCH"); tick(world, .13);
        near(a.combat.health, 115, "No self damage"); near(b.combat.health, 92, "PvP punch damage");
        near(a.combat.multiplier, 1.05, "PvP grants damage stack");
        double pushedZ = b.z; tick(world, .1); check(b.z < pushedZ, "PvP knockback is authoritative");
        world.action(a.id, 3, "Mage"); check(a.className.equals("Gauntlet"), "Cannot switch during attack");
        tick(world, 2);
        position(a, 0, 0); position(b, 0, -1.4); a.combat.multiplier = 1;
        world.action(a.id, 0, "PUMMEL"); tick(world, .3);
        world.input(b.id, 1, 0, 0, true); tick(world, .1);
        near(b.x, 0, "Grab prevents target escaping by input");
        tick(world, .85); near(b.combat.health, 65, "PvP Pummel three-hit total after one regeneration tick");
        near(a.combat.multiplier, 1.15, "PvP Pummel stacks every hit");
        world.input(b.id, 0, 0, 0, false);
        tick(world, .5);
        position(a, 0, 0); position(b, 0, -2);
        a.combat.multiplier = 1;
        world.action(a.id, 0, "SHOCKWAVE"); tick(world, .31);
        near(b.body.slowAmount, .7, "PvP Shockwave slow");
        double startX = b.x; world.input(b.id, 1, 0, 0, false); tick(world, .1);
        near(b.x - startX, .12, "Slow reduces player walking speed");
        world.input(b.id, 0, 0, 0, false);
        b.body.health = 0; world.tick(.01);
        check(b.combat.health == 0 && b.respawnTime == 3, "Death starts player respawn");
        tick(world, 3.01); near(b.combat.health, 115, "Player respawns at class max HP");
        near(b.combat.multiplier, 1, "Death resets multiplier");
        position(b, 5, 5); world.action(b.id, 1, ""); world.tick(.01); check(b.y > 0, "Network jump");
        world.action(b.id, 1, ""); check(b.jumps == 2, "Network double jump");
        world.action(b.id, 1, ""); check(b.jumps == 2, "No third jump");
        world.action(b.id, 2, ""); check(b.verticalSpeed == -24, "Network ground pound");
        world.input(b.id, Double.NaN, 0, 0, false); check(Double.isFinite(b.dx), "Reject invalid input");
        world.remove(a.id); check(world.players.size() == 1, "Remove disconnected player");
        for (int i = 0; i < 7; i++) world.add("Guest", false);
        try { world.add("Overflow", false); throw new AssertionError("Lobby limit ignored"); }
        catch (IllegalArgumentException expected) { }
        LobbyWorld turning = new LobbyWorld();
        LobbyWorld.Player attacker = turning.add("Turn", true), target = turning.add("Target", false);
        turning.action(attacker.id, 4, ""); turning.action(attacker.id, 3, "Gauntlet");
        position(attacker, 0, 0); position(target, -1.4, 0);
        turning.action(attacker.id, 0, "PUNCH");
        turning.input(attacker.id, 0, 0, Math.PI / 2, false);
        tick(turning, .13);
        near(target.combat.health, 77, "LAN Punch turns toward target during startup");
        near(attacker.combat.facing, attacker.cubeYaw, "LAN hitbox tracks visible body facing");
        System.out.println("Passed: lobby rules, server movement, class equip, PvP damage/grab/knockback/slow, respawn, jump, player limit.");
    }
    static void networking() throws Exception {
        try (LobbyNetwork.Server server = new LobbyNetwork.Server(0);
             LobbyNetwork.Client host = new LobbyNetwork.Client("127.0.0.1",server.port(),"Host",server.hostKey);
             LobbyNetwork.Client guest = new LobbyNetwork.Client("127.0.0.1",server.port(),"Guest", "")) {
            waitFor(() -> guest.snapshot != null && guest.snapshot.players.size() == 2, "roster synchronization");
            check(host.host && !guest.host, "Host role handshake");
            host.action(4, "");
            waitFor(() -> host.snapshot != null && host.snapshot.started && guest.snapshot.started, "shared match start");
            double before = guest.snapshot.player(host.id).x; host.dx = 1;
            waitFor(() -> guest.snapshot.player(host.id).x > before + .15, "remote movement"); host.dx = 0;
            host.action(3, "Gauntlet"); guest.action(3, "Gauntlet");
            waitFor(() -> host.snapshot.player(host.id).health == 115 && guest.snapshot.player(guest.id).health == 115, "network equipment");
            Thread.sleep(60);
            synchronized (server.world) {
                position(server.world.players.get(host.id), 0, -1.5);
                position(server.world.players.get(guest.id), 6, 6);
            }
            host.action(0, "PUNCH");
            waitFor(() -> host.snapshot.dummies.get(1).health == 77 && guest.snapshot.dummies.get(1).health == 77,
                    "shared dummy damage");
            waitFor(() -> guest.snapshot.effects.stream().anyMatch(e->e.kind==GameEffect.Kind.HIT),
                    "confirmed hit particles replicate to guest");
            long effectId=guest.snapshot.lastEffectId;
            check(effectId>0 && guest.snapshot.effects.stream().allMatch(e->e.id>0),"Network particle events carry sequence IDs");
            synchronized (server.world) {
                LobbyWorld.Player a = server.world.players.get(host.id);
                a.combat.cancelAttack(); a.combat.cooldowns[0] = 0; a.combat.multiplier = 1;
                position(a, 0, 0); position(server.world.players.get(guest.id), 0, -1.4);
            }
            host.action(0, "PUNCH");
            waitFor(() -> guest.snapshot.player(guest.id).health == 92 && host.snapshot.player(guest.id).health == 92,
                    "shared PvP damage");
            host.yaw = Math.PI / 2;
            waitFor(() -> guest.snapshot.player(host.id).facing > 1.3, "attack facing synchronizes after turning");
            LobbyNetwork.State turned = guest.snapshot.player(host.id);
            near(turned.facing, turned.yaw, "Snapshot hitbox facing matches visible player facing");
            synchronized(server.world) {
                LobbyWorld.Player a=server.world.players.get(host.id);
                a.combat.cancelAttack();a.combat.cooldowns[2]=0;a.combat.multiplier=1;a.combat.streakTime=0;
                position(a,0,0);a.y=a.body.lift=3;
                position(server.world.players.get(guest.id),6,6);
                CombatSystem.Dummy elevated=server.world.arena.dummies.get(1);
                elevated.x=0;elevated.z=-2;elevated.lift=3;elevated.verticalSpeed=0;elevated.health=100;
            }
            host.action(0,"SHOCKWAVE");
            waitFor(()->guest.snapshot.player(host.id).waveTime>0 && guest.snapshot.player(host.id).waveY>1,
                    "airborne shockwave height synchronizes");
            waitFor(()->guest.snapshot.dummies.get(1).health==90 && host.snapshot.dummies.get(1).health==90,
                    "airborne 3D hit damage synchronizes");
            try (LobbyNetwork.Client late = new LobbyNetwork.Client("127.0.0.1",server.port(),"Late join", "")) {
                waitFor(() -> late.snapshot != null && late.snapshot.started && late.snapshot.players.size() == 3,
                        "join in progress");
            }
            waitFor(() -> host.snapshot.players.size() == 2 && guest.snapshot.players.size() == 2, "disconnect roster cleanup");
            // Reject oversized frames without terminating the lobby.
            try (Socket invalid = new Socket("127.0.0.1",server.port())) {
                invalid.setSoTimeout(2000);
                new DataOutputStream(invalid.getOutputStream()).writeInt(Integer.MAX_VALUE);
                check(invalid.getInputStream().read() == -1, "Invalid frames are disconnected");
            }
            check(!host.closed, "Malformed peer does not disconnect healthy clients");
            host.close(); waitFor(() -> guest.closed, "host shutdown closes guest connection");
            check(guest.error != null, "Connection loss has readable feedback");
        }
        System.out.println("Passed: real TCP host/join, snapshots, start, movement, equipment, shared dummy/PvP damage, late join, disconnects, malformed frames, host shutdown.");
    }
    static void ui() throws Exception {
        final CubeMovement[] game = new CubeMovement[1];
        final LobbyPanel[] panel = new LobbyPanel[1];
        int port;
        try (ServerSocket reserve = new ServerSocket(0)) { port = reserve.getLocalPort(); }
        SwingUtilities.invokeAndWait(() -> {
            try {
                game[0] = new CubeMovement(); game[0].setSize(1000,700); game[0].doLayout();
                panel[0] = (LobbyPanel) MovementCheck.field("lobby").get(game[0]);
                game[0].getActionMap().get("lobby").actionPerformed(null);
                check(panel[0].isVisible(), "F2 opens lobby screen");
                MovementCheck.key(game[0], java.awt.event.KeyEvent.VK_R, false, false);
                check(!MovementCheck.field("classMenuOpen").getBoolean(game[0]), "Typing R in lobby cannot open classes");
                panel[0].port.setValue(port); panel[0].name.setText("UI Host"); panel[0].host.doClick();
                check(!panel[0].host.isEnabled(), "Host button disables during connection");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        final LobbyNetwork.Client[] client = new LobbyNetwork.Client[1];
        waitFor(() -> {
            try { SwingUtilities.invokeAndWait(() -> {
                try { client[0] = (LobbyNetwork.Client) MovementCheck.field("lobbyClient").get(game[0]); }
                catch (Exception error) { throw new RuntimeException(error); }
            }); } catch (Exception error) { throw new RuntimeException(error); }
            return client[0] != null && client[0].snapshot != null;
        }, "UI hosting worker completion");
        SwingUtilities.invokeAndWait(() -> {
            check(panel[0].start.isEnabled(), "Host has Start Match control");
            check(game[0].executeCommand("damage 20").startsWith("Leave the LAN"), "Lobby blocks gameplay dev commands");
            check(game[0].executeCommand("help").contains("cooldowns"), "Lobby allows help");
            check(panel[0].mapPicker.maps.isEnabled(),"Host can choose map");
            panel[0].mapPicker.maps.setSelectedIndex(1);
            panel[0].start.doClick();
        });
        waitFor(() -> client[0].snapshot.started, "UI starts match");
        check(client[0].snapshot.map.name.equals("Practice"),"Host picker selection reaches LAN snapshot");
        SwingUtilities.invokeAndWait(() -> {
            try { MovementCheck.field("yaw").setDouble(game[0], 1.2); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        waitFor(() -> client[0].snapshot.player(client[0].id).facing > 1, "UI turned facing arrives");
        client[0].action(1,"");
        waitFor(() -> client[0].snapshot.effects.stream().anyMatch(e->e.kind==GameEffect.Kind.TAKEOFF),"Network takeoff effect arrives");
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.lang.reflect.Method update = CubeMovement.class.getDeclaredMethod("updateNetwork",double.class);
                update.setAccessible(true); update.invoke(game[0],.02);
                CombatSystem currentCombat = (CombatSystem) MovementCheck.field("combat").get(game[0]);
                LobbyNetwork.Snapshot applied = (LobbyNetwork.Snapshot) MovementCheck.field("appliedSnapshot").get(game[0]);
                near(currentCombat.facing, applied.player(client[0].id).facing, "UI applies network attack facing to debug hitbox");
                ParticleSystem visuals=(ParticleSystem)MovementCheck.field("particles").get(game[0]);
                check(!visuals.particles.isEmpty(),"UI generates particles from LAN events while locally paused");
                int particleCount=visuals.particles.size();
                update.invoke(game[0],.02);
                check(visuals.particles.size()==particleCount,"Repeated snapshots do not duplicate particle bursts");
                check(game[0].executeCommand("particles low").contains("160"),"Particle quality command works in LAN");
                check(!panel[0].isVisible(), "Start hides waiting room");
                java.awt.Graphics2D graphics = new java.awt.image.BufferedImage(1000,700,
                        java.awt.image.BufferedImage.TYPE_INT_RGB).createGraphics();
                game[0].paint(graphics); graphics.dispose();
                game[0].getActionMap().get("lobby").actionPerformed(null);
                panel[0].leave.doClick();
                check(MovementCheck.field("lobbyClient").get(game[0]) == null, "Leave disconnects client");
                check(MovementCheck.field("lobbyServer").get(game[0]) == null, "Leave closes owned server");
                check(panel[0].host.isEnabled(), "Leave restores connection controls");
                CombatSystem combat = (CombatSystem) MovementCheck.field("combat").get(game[0]);
                check(combat.dummies.isEmpty() && combat.health == 100 && !MovementCheck.field("mapLoaded").getBoolean(game[0]), "Leave unloads arena and resets player");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        System.out.println("Passed: lobby UI host worker, start/leave controls, typing guard, multiplayer dev restrictions, rendering, offline restoration.");
    }
    static void failureHandling()throws Exception{
        check(!LobbyDiagnostics.describe(new EOFException()).contains("null"),"EOF has actionable message");
        check(LobbyDiagnostics.describe(new NullPointerException()).contains("NullPointerException"),"Message-less exceptions retain type");
        check(LobbyDiagnostics.describe(new java.util.concurrent.ExecutionException(new BindException())).contains("port"),"Worker errors are unwrapped");
        int port;
        try(LobbyNetwork.Server server=new LobbyNetwork.Server(0)){
            port=server.port();try{LobbyNetwork.Server duplicate=new LobbyNetwork.Server(port);duplicate.close();throw new AssertionError("Duplicate host accepted");}catch(BindException expected){}
            try(LobbyNetwork.Client host=new LobbyNetwork.Client("127.0.0.1",port,"Host",server.hostKey)){
                waitFor(()->host.snapshot!=null,"Original host still usable after bind failure");
            }
        }
        try(LobbyNetwork.Server restarted=new LobbyNetwork.Server(port);LobbyNetwork.Client host=new LobbyNetwork.Client("127.0.0.1",port,"Restart",restarted.hostKey)){
            waitFor(()->host.snapshot!=null,"Same-port rehost succeeds");
        }
        try(ServerSocket abrupt=new ServerSocket(0)){
            Thread peer=new Thread(()->{try(Socket socket=abrupt.accept()){socket.getInputStream().read(new byte[4096]);}catch(IOException ignored){}});peer.setDaemon(true);peer.start();
            try(LobbyNetwork.Client client=new LobbyNetwork.Client("127.0.0.1",abrupt.getLocalPort(),"Handshake","")){throw new AssertionError("Abrupt peer accepted");}
            catch(IOException expected){check(!LobbyDiagnostics.describe(expected).contains("null"),"Abrupt handshake reports meaningful failure");}peer.join(2000);
        }
        System.out.println("Passed: message-less handshake failures, occupied port rejection, original host survival and same-port rehosting.");
    }
    static void mapCache() throws Exception {
        MapDefinition map=MapStorage.practice();
        MapDefinition.Part part=new MapDefinition.Part();part.x=8;part.z=8;map.objects.add(part);
        byte[] bytes=map.encode();LobbyNetwork.MapCache cache=new LobbyNetwork.MapCache();
        MapDefinition first=cache.read(bytes);first.objects.get(first.objects.size()-1).x=42;
        MapDefinition second=cache.read(bytes);
        check(cache.decodes==1,"Unchanged map decoded once");
        check(second.objects.get(second.objects.size()-1).x==8,"Snapshots do not share mutable map positions");
        map.name="Changed map";check(cache.read(map.encode()).name.equals("Changed map")&&cache.decodes==2,"Changed map invalidates cache");
        for(int i=0;i<100;i++){MapDefinition.decode(bytes);cache.read(bytes);}
        long start=System.nanoTime();for(int i=0;i<1000;i++)MapDefinition.decode(bytes);long parsing=System.nanoTime()-start;
        start=System.nanoTime();for(int i=0;i<1000;i++)cache.read(bytes);long cached=System.nanoTime()-start;
        System.out.println("Passed: map cache isolation/invalidation; 1000 map loads: parse="+parsing/1000000.0+"ms cached="+cached/1000000.0+"ms");
    }
    public static void main(String[] args) {
        try { mapCache();failureHandling();simulation(); networking(); ui(); System.exit(0); }
        catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
}
