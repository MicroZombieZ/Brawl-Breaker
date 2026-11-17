/** 3D collision, airborne abilities, and shared LAN hit detection checks. */
public class HitboxCheck {
    static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static void near(double value,double expected,String message) { check(Math.abs(value-expected)<1e-7,message+": "+value); }
    static CombatSystem arena() {
        CombatSystem c=new CombatSystem();c.equip(true);c.externalTargetUpdates=true;
        for(CombatSystem.Dummy d:c.dummies) { d.x=9;d.z=9; }
        return c;
    }
    static CombatSystem.Dummy target(CombatSystem c,int index,double x,double y,double z) {
        CombatSystem.Dummy d=c.dummies.get(index);d.x=x;d.lift=y;d.z=z;return d;
    }
    static void geometry() {
        CombatSystem.Hitbox box=CombatSystem.hitbox(CombatSystem.Move.PUNCH,0,3,0,0);
        CombatSystem.Dummy d=new CombatSystem.Dummy(1.45,-1);d.lift=3;
        check(box.overlaps(d),"Target hurtbox edge overlaps full attack box");
        d.x=1.51;check(!box.overlaps(d),"Reject horizontal gap beyond target edge");
        d.x=1.65;d.yaw=Math.PI/4;check(box.overlaps(d),"Rotated hurtbox corner overlaps");
        d.x=1.8;check(!box.overlaps(d),"SAT rejects separated rotated boxes");
        d.x=0;d.z=-1;d.lift=4.51;check(!box.overlaps(d),"Reject targets above box");
        d.lift=.9;check(!box.overlaps(d),"Reject targets entirely below box");
        d.lift=1.1;check(box.overlaps(d),"Use target height rather than feet-distance cutoff");
        d.playerBody=true;check(!box.overlaps(d),"Player hurtbox is 1 unit high, NPC is 1.5");
        d.playerBody=false;d.lift=6;
        check(Double.isFinite(box.sweep(d,0,4,0)),"Swept box includes vertical translation");
        check(!Double.isFinite(box.sweep(d,0,0,-3)),"Horizontal sweep cannot hit vertically distant target");
        double[][] vertices=box.vertices();
        near(vertices[0][1],2.5,"Lower hitbox Y");near(vertices[4][1],4.5,"Upper hitbox Y");
        CombatSystem.Hitbox turned=CombatSystem.hitbox(CombatSystem.Move.PUNCH,5,7,2,Math.PI/2);
        near(turned.x,4,"Box stays in front after turning");near(turned.y,7.5,"Box follows player height");
        near(turned.z,2,"Box follows player position");
    }
    static void attacks() {
        CombatSystem c=arena();CombatSystem.Dummy d=target(c,0,0,3,-1.4);
        check(c.attack(CombatSystem.Move.PUNCH,0,3,0,0),"Airborne Punch starts");
        c.update(.13,0,3,0);near(d.health,77,"Airborne Punch damage");
        c.update(.1,0,3,0);near(d.health,77,"Punch does not damage repeatedly");
        c=arena();d=target(c,0,0,0,-1.4);c.attack(CombatSystem.Move.PUNCH,0,3,0,0);
        c.update(.13,0,3,0);near(d.health,100,"Air Punch misses ground target outside Y bounds");

        c=arena();d=target(c,0,0,3,-1.4);
        check(c.attack(CombatSystem.Move.PUMMEL,0,3,0,0),"Airborne Pummel starts");
        c.update(.21,0,3,0);check(c.grabbedTarget()==d,"Airborne grab connects");
        c.update(.24,1,4,0,Math.PI/2);
        near(d.lift,4,"Grabbed target follows vertical movement");near(d.x,-.1,"Grabbed target follows position and turn");
        c.update(.8,1,4,0,Math.PI/2);
        near(d.health,70,"Air Pummel keeps three-hit damage");near(d.lift,4,"Release does not teleport target to floor");
        c.externalTargetUpdates=false;c.update(.1,1,4,0);
        check(d.lift>0 && d.lift<4 && d.verticalSpeed<0,"Released NPC falls naturally");
        c.update(1,1,4,0);near(d.lift,0,"Falling NPC lands on floor");

        c=arena();CombatSystem.Dummy inner=target(c,0,0,3,-2),outer=target(c,1,0,3,-4),below=target(c,2,0,0,-1);
        check(c.attack(CombatSystem.Move.SHOCKWAVE,0,3,0,0),"Airborne Shockwave starts");
        c.update(.31,0,3,0);near(inner.health,90,"Air Shockwave inner damage");
        near(outer.slowAmount,.4,"Air Shockwave outer slow");near(below.health,100,"Shockwave rejects lower target");
        near(below.slowTime,0,"No slow through vertical gap");near(c.waveY,3,"Shockwave visual follows impact height");
        check(c.effects.stream().anyMatch(e->e.kind==GameEffect.Kind.SHOCKWAVE&&e.y==3),"Shockwave particles use airborne height");

        c=arena();d=target(c,0,0,3,-2.5);
        check(c.attack(CombatSystem.Move.HAYMAKER,0,3,0,0),"Airborne Hay-maker starts");
        c.update(.3,0,3,0);near(d.health,55,"Air dash sweeps through target");
        c=arena();d=target(c,0,0,0,-2.5);c.attack(CombatSystem.Move.HAYMAKER,0,3,0,0);
        c.update(.3,0,3,0);near(d.health,100,"Air dash misses target below swept box");
        check(c.endLag>1,"Missed air dash keeps recovery");
    }
    static void lan() {
        LobbyWorld world=new LobbyWorld();LobbyWorld.Player a=world.add("Air",true),b=world.add("Target",false);
        world.action(a.id,3,"Gauntlet");world.action(a.id,4,"");
        LobbyCheck.position(a,0,0);LobbyCheck.position(b,0,-1.4);
        a.y=a.body.lift=3;b.y=b.body.lift=3.6;
        world.action(a.id,0,"PUNCH");LobbyCheck.tick(world,.13);
        near(b.combat.health,77,"LAN uses same 3D airborne Punch");
        a.combat.cancelAttack();LobbyCheck.position(a,0,0);LobbyCheck.position(b,0,-1.4);
        a.y=a.body.lift=3;b.y=b.body.lift=3;
        world.action(a.id,0,"PUMMEL");LobbyCheck.tick(world,.3);
        check(a.combat.grabbedTarget()==b.body,"LAN airborne Pummel grabs player");
        check(b.y>1 && Math.abs(b.y-a.y)<.6,"LAN grabbed player remains at attacker height");
        LobbyCheck.tick(world,1.1);
        check(b.y>=0 && Double.isFinite(b.y),"LAN target resumes vertical physics after release");
    }
    public static void main(String[] args) {
        geometry();attacks();lan();
        System.out.println("Passed: oriented box overlap/sweep, hurtbox dimensions, airborne damage/grab/dash/AOE, vertical rejection, release gravity, LAN parity.");
    }
}
