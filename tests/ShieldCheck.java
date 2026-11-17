import java.util.*;
import javax.swing.*;

public class ShieldCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void near(double a,double b,String message){check(Math.abs(a-b)<1e-6,message+": "+a+" != "+b);}
    static CombatSystem shield(){CombatSystem c=new CombatSystem();c.equipClass("Shield");c.clearDummies();c.map.width=c.map.depth=100;return c;}
    static void tick(CombatSystem c,double duration){while(duration>1e-9){double dt=Math.min(.01,duration);c.update(dt,c.playerX,c.playerY,c.playerZ,c.facing);duration-=dt;}}
    static void combat(){
        CombatSystem c=shield();near(c.maxHealth(),100,"Shield HP");near(c.walkSpeed(),4.5,"Assault walk");near(c.sprintSpeed(),7.5,"Assault sprint");
        CombatSystem.Dummy d=new CombatSystem.Dummy(0,-1.5);c.dummies.add(d);check(c.attack(CombatSystem.Move.PUNCH,0,0,0,0),"Bash starts");near(c.cooldowns[0],1.15,"Bash cooldown");tick(c,.079);near(d.health,100,"Bash windup");tick(c,.002);near(d.health,76.5,"Bash damage");near(c.multiplier,1,"No Gauntlet streak on Shield");check(c.endLag>.33&&c.endLag<=.35,"Bash extended recovery");
        c=shield();d=new CombatSystem.Dummy(0,-2);c.dummies.add(d);c.attack(CombatSystem.Move.SHOCKWAVE,0,0,0,0);near(c.cooldowns[2],50,"Swing cooldown");tick(c,1.29);near(d.health,100,"Swing windup");tick(c,.02);near(d.health,65,"Swing damage");check(c.endLag>0&&c.endLag<=.35,"Swing hit recovery");
        c=shield();c.attack(CombatSystem.Move.SHOCKWAVE,0,2,0,0);tick(c,1.3);near(c.endLag,1.5,"Swing miss recovery airborne");
        c=shield();d=new CombatSystem.Dummy(0,-3);c.dummies.add(d);c.attack(CombatSystem.Move.PUMMEL,0,0,0,0);near(c.cooldowns[1],40,"Charge cooldown");tick(c,.99);near(c.playerZ,0,"Charge windup has no automatic dash");tick(c,.4);near(d.health,75,"Charge damage");check(d.vz<0&&c.playerZ>-3,"Charge knocks back and stops on target");
        c=shield();c.attack(CombatSystem.Move.PUMMEL,0,0,0,0);tick(c,2.5);near(c.playerZ,-15,"Charge range");near(c.endLag,.8,"Charge miss recovery");
        c=shield();MapDefinition.Part wall=new MapDefinition.Part();wall.z=-2;wall.w=5;wall.h=3;wall.d=.1;c.map.objects.add(wall);c.attack(CombatSystem.Move.PUMMEL,0,0,0,0);tick(c,1.2);check(c.playerZ>=-1.45-1e-6&&c.endLag>1.4,"Charge wall collision and long recovery");
        c=shield();c.attack(CombatSystem.Move.PUMMEL,0,0,0,0);tick(c,1);c.update(.1,0,0,0,Math.PI);near(Math.abs(c.facing),Math.PI/40,"Charge limited turning");
        c=shield();check(c.attack(CombatSystem.Move.HAYMAKER,0,0,0,0)&&c.protect,"Mode toggles Protect");near(c.cooldowns[3],25,"Mode cooldown");near(c.walkSpeed(),3,"Protect walk");near(c.sprintSpeed(),5,"Protect sprint");check(!c.attack(CombatSystem.Move.HAYMAKER,0,0,0,0),"Mode respects cooldown");tick(c,25);check(c.attack(CombatSystem.Move.HAYMAKER,0,0,0,0)&&!c.protect,"Mode returns Assault");
        for(int side=0;side<3;side++){c=shield();d=new CombatSystem.Dummy(0,0);d.defender=shield();d.defender.protect=true;d.playerBody=true;c.dummies.add(d);double x=side==2?1.5:0,z=side==0?-1.5:side==1?1.5:0,yaw=side==0?Math.PI:side==1?0:Math.PI/2;c.attack(CombatSystem.Move.PUNCH,x,0,z,yaw);tick(c,.08);near(d.health,100-23.5*(side==0?.7:1),"Directional protection side "+side);}
        c=shield();d=new CombatSystem.Dummy(0,-1.5);c.dummies.add(d);wall=new MapDefinition.Part();wall.z=-.8;wall.d=.1;wall.h=3;wall.w=4;c.map.objects.add(wall);c.attack(CombatSystem.Move.PUNCH,0,0,0,0);tick(c,.08);near(d.health,100,"Bash cannot reach through wall");
    }
    static void integration()throws Exception{
        SwingUtilities.invokeAndWait(()->{try{CubeMovement demo=new CubeMovement();CombatSystem c=(CombatSystem)MovementCheck.field("combat").get(demo);c.equipClass("Shield");c.clearDummies();MovementCheck.field("paused").setBoolean(demo,false);MovementCheck.key(demo,java.awt.event.KeyEvent.VK_W,false,false);java.lang.reflect.Method move=CubeMovement.class.getDeclaredMethod("move",double.class);move.setAccessible(true);move.invoke(demo,.1);near(MovementCheck.field("z").getDouble(demo),-.45,"Solo Shield speed");c.protect=true;move.invoke(demo,.1);near(MovementCheck.field("z").getDouble(demo),-.75,"Solo Protect speed");}catch(Exception e){throw new RuntimeException(e);}});
        LobbyWorld world=new LobbyWorld();world.mapChoice=MapStorage::practice;LobbyWorld.Player a=world.add("Shield",true);world.action(a.id,3,"Shield");world.action(a.id,4,"");a.x=a.body.x=0;a.z=a.body.z=0;world.input(a.id,0,-1,0,false);world.tick(.1);near(a.z,-.45,"LAN Shield speed");world.action(a.id,0,"HAYMAKER");world.tick(.1);near(a.z,-.75,"LAN Protect speed");
        LobbyWorld duel=new LobbyWorld();duel.mapChoice=MapStorage::practice;LobbyWorld.Player attacker=duel.add("Attacker",true),defender=duel.add("Defender",false);
        duel.action(attacker.id,3,"Gauntlet");duel.action(defender.id,3,"Shield");duel.action(attacker.id,4,"");duel.arena.clearDummies();
        attacker.x=attacker.body.x=0;attacker.z=attacker.body.z=-1.5;attacker.yaw=attacker.cubeYaw=Math.PI;
        defender.x=defender.body.x=0;defender.z=defender.body.z=0;duel.action(defender.id,0,"HAYMAKER");duel.action(attacker.id,0,"PUNCH");
        for(int i=0;i<20;i++)duel.tick(.01);near(defender.combat.health,100-23*.7,"Host applies frontal Protect to incoming Gauntlet damage");
        try(LobbyNetwork.Server server=new LobbyNetwork.Server(0);LobbyNetwork.Client host=new LobbyNetwork.Client("127.0.0.1",server.port(),"Shield",server.hostKey)){
            host.action(3,"Shield");host.action(0,"HAYMAKER");host.action(4,"");host.action(0,"HAYMAKER");
            long end=System.currentTimeMillis()+5000;while((host.snapshot==null||!host.snapshot.started||!host.snapshot.player(host.id).className.equals("Shield"))&&System.currentTimeMillis()<end)Thread.sleep(20);
            check(host.snapshot!=null&&host.snapshot.player(host.id).className.equals("Shield"),"Shield class transmitted");
            synchronized(server.world){server.world.players.get(host.id).combat.protect=true;}
            end=System.currentTimeMillis()+5000;while(!host.snapshot.player(host.id).protect&&System.currentTimeMillis()<end)Thread.sleep(20);check(host.snapshot.player(host.id).protect,"Protect mode synchronized");
        }
    }
    static void regenerationAndCharge() throws Exception {
        for(String name:new String[]{"Gauntlet","Shield","Mage","Ranger","Rogue"}){
            CombatSystem c=new CombatSystem();c.equipClass(name);c.clearDummies();c.damagePlayer(10);double damaged=c.health;
            tick(c,1.29);near(c.health,damaged,"Healing waits for interval "+name);
            tick(c,.01);near(c.health,damaged+3,"Healing tick "+name);
            tick(c,.7);c.damagePlayer(1);double hit=c.health;tick(c,.6);near(c.health,hit,"Damage restarts healing timer");
            tick(c,10);near(c.health,c.maxHealth(),"Healing capped");c.damagePlayer(1000);tick(c,10);near(c.health,0,"No healing while defeated");
        }
        LobbyWorld world=new LobbyWorld();world.mapChoice=MapStorage::practice;LobbyWorld.Player p=world.add("Host",true);
        world.action(p.id,3,"Shield");world.action(p.id,4,"");world.arena.clearDummies();p.combat.health=p.body.health=90;
        for(int i=0;i<65;i++)world.tick(.02);near(p.combat.health,93,"LAN regeneration");near(p.body.health,93,"LAN body health synchronized");
        for(int input=0;input<=1;input++){
            p.combat.cancelAttack();Arrays.fill(p.combat.cooldowns,0);p.x=p.body.x=0;p.z=p.body.z=0;
            world.input(p.id,0,input,0,false);world.action(p.id,0,"PUMMEL");
            for(int i=0;i<50;i++)world.tick(.02);double start=p.z;
            for(int i=0;i<10;i++)world.tick(.02);near(p.z,start-2,"LAN charge forces forward with input "+input);
        }
        SwingUtilities.invokeAndWait(()->{try{
            CubeMovement game=new CubeMovement();MovementCheck.field("paused").setBoolean(game,false);CombatSystem c=(CombatSystem)MovementCheck.field("combat").get(game);c.equipClass("Shield");c.clearDummies();
            java.lang.reflect.Method move=CubeMovement.class.getDeclaredMethod("move",double.class);move.setAccessible(true);
            for(int input=0;input<=1;input++){
                c.cancelAttack();Arrays.fill(c.cooldowns,0);MovementCheck.field("x").setDouble(game,0);MovementCheck.field("z").setDouble(game,0);
                if(input==1)MovementCheck.key(game,java.awt.event.KeyEvent.VK_S,false,false);
                c.attack(CombatSystem.Move.PUMMEL,0,0,0,0);for(int i=0;i<100;i++)move.invoke(game,.01);
                double start=MovementCheck.field("z").getDouble(game);for(int i=0;i<20;i++)move.invoke(game,.01);
                near(MovementCheck.field("z").getDouble(game),start-2,"Solo forced charge input "+input);
            }
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    public static void main(String[] args){try{combat();integration();regenerationAndCharge();System.out.println("Passed: Shield damage/cooldowns, windups, charge walls/turning, mode speeds, frontal protection, solo/LAN parity and network mode state.");System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
