import java.util.ArrayList;
import java.util.List;

/** Combat simulation in world units and seconds, independent of Swing and rendering. */
final class CombatSystem {
    enum Move {
        PUNCH("LMB  Punch", 1.7), PUMMEL("Q  Pummel", 45),
        SHOCKWAVE("E  Shockwave", 25), HAYMAKER("F  Hay-maker", 50);
        final String label;
        final double cooldown;
        Move(String label, double cooldown) { this.label = label; this.cooldown = cooldown; }
    }

    static final double BASE_HP = 100, GAUNTLET_HP = 115, STREAK_DURATION = 6.5;
    static final double MAX_MULTIPLIER = 1.35, MULTIPLIER_STEP = .05;
    static final double INNER_RADIUS = 2.5, OUTER_RADIUS = 5;
    static final double ATTACK_HEIGHT = 2;
    /** Upright oriented box. SAT tests include target width, depth, and height. */
    static final class Hitbox {
        final double x,y,z,width,height,depth,yaw;
        Hitbox(double x,double y,double z,double width,double height,double depth,double yaw) {
            this.x=x;this.y=y;this.z=z;this.width=width;this.height=height;this.depth=depth;this.yaw=yaw;
        }
        double[][] vertices() {
            double[][] result={{-.5,-.5,-.5},{.5,-.5,-.5},{.5,-.5,.5},{-.5,-.5,.5},
                    {-.5,.5,-.5},{.5,.5,-.5},{.5,.5,.5},{-.5,.5,.5}};
            for(double[] v:result) {
                double lx=v[0]*width,lz=v[2]*depth;
                v[0]=x+Math.cos(yaw)*lx+Math.sin(yaw)*lz;
                v[1]=y+v[1]*height;v[2]=z-Math.sin(yaw)*lx+Math.cos(yaw)*lz;
            }
            return result;
        }
        boolean verticalOverlap(Dummy target) {
            return y-height/2<=target.lift+target.height()+1e-9 && y+height/2>=target.lift-1e-9;
        }
        boolean overlaps(Dummy target) { return Double.isFinite(sweep(target,0,0,0)); }
        // Earliest contact while translating this box; no sampling gaps at high dash speeds.
        double sweep(Dummy target,double dx,double dy,double dz) {
            double[][] axes={{Math.cos(yaw),-Math.sin(yaw)},{Math.sin(yaw),Math.cos(yaw)},
                    {Math.cos(target.yaw),-Math.sin(target.yaw)},{Math.sin(target.yaw),Math.cos(target.yaw)}};
            double enter=0,exit=1;
            for(int i=0;i<5;i++) {
                double distance,velocity,radius;
                if(i==4) {
                    distance=target.lift+target.height()/2-y;velocity=dy;
                    radius=(height+target.height())/2;
                } else {
                    double ax=axes[i][0],az=axes[i][1];
                    distance=(target.x-x)*ax+(target.z-z)*az;velocity=dx*ax+dz*az;
                    radius=width/2*Math.abs(ax*Math.cos(yaw)-az*Math.sin(yaw))
                            +depth/2*Math.abs(ax*Math.sin(yaw)+az*Math.cos(yaw))
                            +.5*Math.abs(ax*Math.cos(target.yaw)-az*Math.sin(target.yaw))
                            +.5*Math.abs(ax*Math.sin(target.yaw)+az*Math.cos(target.yaw));
                }
                if(Math.abs(velocity)<1e-12) {
                    if(Math.abs(distance)>radius+1e-9) return Double.POSITIVE_INFINITY;
                } else {
                    double a=(distance-radius)/velocity,b=(distance+radius)/velocity;
                    enter=Math.max(enter,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));
                    if(enter>exit+1e-9) return Double.POSITIVE_INFINITY;
                }
            }
            return enter;
        }
    }
    static Hitbox hitbox(Move move,double x,double y,double z,double facing) {
        if(move==Move.SHOCKWAVE) return new Hitbox(x,y+.5,z,OUTER_RADIUS*2,ATTACK_HEIGHT,OUTER_RADIUS*2,0);
        double reach=move==Move.PUNCH?2:move==Move.PUMMEL?1.8:.95;
        double width=move==Move.HAYMAKER?1.9:reach;
        return new Hitbox(x-Math.sin(facing)*reach/2,y+.5,z-Math.cos(facing)*reach/2,
                width,ATTACK_HEIGHT,reach,facing);
    }
    static final class Dummy {
        final double spawnX, spawnZ;
        double spawnY;
        double x, z, health = 100, maxHealth = 100, vx, vz, slowTime, slowAmount, flash, damageTime, lastDamage;
        double respawnTime, lift, verticalSpeed, yaw;
        boolean playerBody;
        CombatSystem defender;
        Dummy(double x, double z) { this.x = spawnX = x; this.z = spawnZ = z; }
        boolean alive() { return health > 0; }
        double height() { return playerBody?1:1.5; }
    }

    final List<Dummy> dummies = new ArrayList<>();
    MapDefinition map=MapStorage.practice();
    final List<GameEffect> effects = new ArrayList<>();
    final double[] cooldowns = new double[Move.values().length];
    boolean equipped,shield,protect;
    static final double[] SHIELD_COOLDOWNS={.45,40,50,25};
    static final String[] SHIELD_LABELS={"LMB  Shield-Bash","Q  Charge","E  Swing","F  Mode"};
    double walkSpeed(){return shield?(protect?3:4.5):4;}
    double sprintSpeed(){return shield?(protect?5:7.5):7;}
    boolean charging(){return shield&&active==Move.PUMMEL&&attackTime>=1-1e-9;}
    double turnStep(double delta,double dt){return charging()?Math.max(-Math.PI/4*dt,Math.min(Math.PI/4*dt,delta)):delta*(1-Math.exp(-10*dt));}
    static Hitbox shieldHitbox(Move move,double x,double y,double z,double facing){double reach=move==Move.SHOCKWAVE?2.5:move==Move.PUNCH?1.7:1.2;return new Hitbox(x-Math.sin(facing)*reach/2,y+.5,z-Math.cos(facing)*reach/2,move==Move.SHOCKWAVE?2.6:1.4,1.6,reach,facing);}
    Hitbox activeHitbox(){return shield?shieldHitbox(active,playerX,playerY,playerZ,facing):hitbox(active,playerX,playerY,playerZ,facing);}

    boolean godMode, cooldownsEnabled = true;
    boolean externalTargetUpdates;
    double dummyMaxHealth = 100;
    double health = BASE_HP, multiplier = 1, streakTime;
    double playerX, playerY, playerZ, speed;
    Move active;
    double attackTime, endLag, facing, attackMultiplier;
    double waveTime, waveX, waveY, waveZ;
    String message = "Equip Gauntlet or Shield with R to start training";
    double messageTime = 5;
    private Dummy grabbed;
    private boolean resolved;
    private int pummelHits;

    CombatSystem() {
        resetDummies();
    }

    void resetDummies() {
        new MapStorage.MapData("session",map).populate(this);
    }

    void clearDummies() {
        if (grabbed != null) finish(0);
        dummies.clear();
    }

    void spawnDummy(double x, double z) {
        double[] location=MapPhysics.boundary(map,x,z,.5);
        Dummy dummy = new Dummy(location[0],location[1]);
        dummy.health = dummy.maxHealth = dummyMaxHealth;
        dummies.add(dummy);
        effect(GameEffect.Kind.RESPAWN,dummy.x,0,dummy.z,1,0);
    }

    void setDummyHealth(double value) {
        dummyMaxHealth = value;
        for (Dummy dummy : dummies) {
            if (!dummy.alive()) effect(GameEffect.Kind.RESPAWN,dummy.x,dummy.lift,dummy.z,1,0);
            dummy.health = dummy.maxHealth = value;
            dummy.respawnTime = 0;
        }
    }

    void cancelAttack() { finish(0); }

    void reset() {
        finish(0);
        equipped = shield = protect = godMode = false;
        cooldownsEnabled = true;
        health = BASE_HP; multiplier = 1; streakTime = 0;
        attackTime = waveTime = waveY = playerX = playerY = playerZ = speed = 0;
        java.util.Arrays.fill(cooldowns, 0);
        dummyMaxHealth = 100;
        resetDummies();
        effects.clear();
        tell("Arena reset - equip Gauntlet or Shield with R");
    }

    double maxHealth() { return equipped&&!shield ? GAUNTLET_HP : BASE_HP; }
    boolean locked() { return active != null || endLag > 0; }

    void equip(boolean value){equipClass(value?"Gauntlet":"");}
    void equipClass(String name){
        boolean nextShield="Shield".equals(name),nextEquipped=nextShield||"Gauntlet".equals(name);
        if(equipped==nextEquipped&&shield==nextShield)return;
        double oldMax=maxHealth();equipped=nextEquipped;shield=nextShield;protect=false;
        health=Math.max(1,Math.min(maxHealth(),health+maxHealth()-oldMax));
        multiplier=1;streakTime=0;
        tell(equipped?name+" equipped - LMB / Q / E / F":"Combat class unequipped");
    }

    boolean attack(Move move, double x, double y, double z, double yaw) {
        if (!equipped || locked() || (cooldownsEnabled && cooldowns[move.ordinal()] > 0) || health <= 0) return false;
        if(shield&&move==Move.HAYMAKER){protect=!protect;cooldowns[3]=cooldownsEnabled?25:0;tell(protect?"Protect: 30% frontal defense":"Assault: increased movement speed");return true;}
        active = move;
        attackTime = 0;
        facing = yaw;
        playerX = x; playerY = y; playerZ = z;
        attackMultiplier = shield?1:multiplier;
        resolved = false;
        grabbed = null;
        pummelHits = 0;
        cooldowns[move.ordinal()] = cooldownsEnabled ? (shield?SHIELD_COOLDOWNS[move.ordinal()]:move.cooldown) : 0;
        effect(GameEffect.Kind.SWING, x, y, z, 1, move.ordinal());
        return true;
    }

    void update(double dt, double x, double y, double z) {
        update(dt, x, y, z, facing);
    }

    void update(double dt, double x, double y, double z, double currentFacing) {
        double delta=Math.IEEEremainder(currentFacing-facing,Math.PI*2);
        facing = Math.IEEEremainder(charging()?facing+Math.max(-Math.PI/4*dt,Math.min(Math.PI/4*dt,delta)):currentFacing, Math.PI * 2);
        playerX = x; playerY = y; playerZ = z;
        for (int i = 0; i < cooldowns.length; i++) cooldowns[i] = Math.max(0, cooldowns[i] - dt);
        endLag = Math.max(0, endLag - dt);
        streakTime = Math.max(0, streakTime - dt);
        if (streakTime == 0) multiplier = 1;
        waveTime = Math.max(0, waveTime - dt);
        messageTime = Math.max(0, messageTime - dt);
        if (!externalTargetUpdates) for (Dummy dummy : dummies) updateDummy(dummy, dt);
        if (active == null) return;
        double before = attackTime;
        attackTime += dt;
        if(shield){updateShield(before);return;}
        switch (active) {
            case PUNCH:
                if (!resolved && attackTime >= .12) {
                    resolved = true;
                    Dummy target = inFront();
                    if (target != null) hit(target, 23, 1.5);
                    else tell("Punch missed");
                }
                if (attackTime >= .36) finish(.08);
                break;
            case PUMMEL:
                if (!resolved && attackTime >= .2) {
                    resolved = true;
                    grabbed = inFront();
                    if (grabbed == null) { tell("Pummel missed - recovering"); finish(1.1); break; }
                    grabbed.vx = grabbed.vz = 0;
                    effect(GameEffect.Kind.GRAB, grabbed.x, grabbed.lift, grabbed.z, 1, 0);
                }
                if (grabbed != null) {
                    double[] hold=MapPhysics.move(map,grabbed.x,grabbed.lift,grabbed.z,playerX-Math.sin(facing)*1.1,playerY + (attackTime < .4 ? .5 * Math.sin((attackTime - .2) / .2 * Math.PI) : 0),playerZ-Math.cos(facing)*1.1,.5,grabbed.height());
                    grabbed.x=hold[0];grabbed.z=hold[2];
                    grabbed.lift = hold[1];
                    grabbed.verticalSpeed = 0;
                    while (pummelHits < 3 && attackTime >= .45 + pummelHits * .25) {
                        if (grabbed.alive()) {
                            if (pummelHits == 0) effect(GameEffect.Kind.SLAM, grabbed.x, grabbed.lift, grabbed.z, 1, 0);
                            hit(grabbed, 10, 0);
                        }
                        pummelHits++;
                    }
                }
                if (attackTime >= 1.2) finish(.25);
                break;
            case SHOCKWAVE:
                if (!resolved && attackTime >= .3) {
                    resolved = true;
                    waveTime = .7; waveX = playerX; waveY = playerY; waveZ = playerZ;
                    effect(GameEffect.Kind.SHOCKWAVE, playerX, playerY, playerZ, 1, 0);
                    int affected = 0;
                    for (Dummy dummy : dummies) {
                        double distance = Math.hypot(dummy.x - playerX, dummy.z - playerZ);
                        if (!dummy.alive() || distance > OUTER_RADIUS
                                || !hitbox(Move.SHOCKWAVE,playerX,playerY,playerZ,facing).verticalOverlap(dummy)) continue;
                        affected++;
                        dummy.slowTime = 3;
                        dummy.slowAmount = distance <= INNER_RADIUS ? .7 : .4;
                        if (distance <= INNER_RADIUS) hit(dummy, 10, 0);
                    }
                    tell("Shockwave: " + affected + " targets slowed");
                }
                if (attackTime >= .65) finish(.2);
                break;
            case HAYMAKER:
                double step = Math.max(0, Math.min(attackTime, .3) - Math.min(before, .3)) * 12;
                double[] next=MapPhysics.move(map,playerX,playerY,playerZ,playerX-Math.sin(facing)*step,playerY,playerZ-Math.cos(facing)*step,.5,1);
                double nextX=next[0],nextZ=next[2];
                Dummy target = sweptTarget(playerX, playerZ, nextX, nextZ);
                boolean wall = Math.abs(nextX - playerX) + Math.abs(nextZ - playerZ) < step * .5;
                playerX = nextX; playerZ = nextZ;
                if (target != null) {
                    hit(target, 45, 8);
                    tell("Hay-maker connected");
                    finish(.3);
                } else if (attackTime >= .3 || wall) {
                    tell("Hay-maker missed - recovering");
                    finish(1.2);
                }
                break;
        }
    }

    private void updateShield(double before){
        if(active==Move.PUNCH||active==Move.SHOCKWAVE){
            boolean swing=active==Move.SHOCKWAVE;double windup=swing?1.3:.08;
            if(!resolved&&attackTime>=windup-1e-9){resolved=true;Dummy target=inFront();if(target!=null){hit(target,swing?35:23.5,swing?4:1.5);finish(swing?.35:.12);}else{tell(swing?"Swing missed - recovering":"Shield-Bash missed");finish(swing?1.5:.12);}}return;
        }
        if(active==Move.PUMMEL){
            double travel=Math.max(0,Math.min(attackTime,2.5)-Math.max(before,1))*10;if(travel<=0)return;
            double ax=playerX,az=playerZ,ay=playerY,dx=-Math.sin(facing)*travel,dz=-Math.cos(facing)*travel;
            double radius=.5*(Math.abs(Math.cos(facing))+Math.abs(Math.sin(facing)));
            double[] next=MapPhysics.move(map,ax,ay,az,ax+dx,ay,az+dz,radius,1);
            Dummy target=null;double first=Double.POSITIVE_INFINITY;Hitbox box=activeHitbox();
            for(Dummy d:dummies)if(d.alive()&&shieldVisible(d)){double t=box.sweep(d,next[0]-ax,next[1]-ay,next[2]-az);if(t<first){first=t;target=d;}}
            boolean wall=Math.hypot(next[0]-ax-dx,next[2]-az-dz)>1e-5&&Math.abs(next[1]-ay)<1e-5;
            double fraction=target==null?1:first;playerX=ax+(next[0]-ax)*fraction;playerY=ay+(next[1]-ay)*fraction;playerZ=az+(next[2]-az)*fraction;
            if(target!=null){hit(target,25,8);tell("Charge connected");finish(.35);}
            else if(wall){tell("Charge hit a wall - recovering");finish(1.5);}
            else if(attackTime>=2.5-1e-9){tell("Charge missed - recovering");finish(.8);}
        }
    }
    private boolean shieldVisible(Dummy target){
        double tx=target.x,ty=target.lift+target.height()/2,tz=target.z;
        double[] reached=MapPhysics.move(map,playerX,playerY+.5,playerZ,tx,ty,tz,.001,.002);
        return Math.abs(reached[0]-tx)+Math.abs(reached[1]-ty)+Math.abs(reached[2]-tz)<1e-6;
    }
    static double frontalDamageFactor(Dummy target,double sourceX,double sourceZ){
        if(target.defender==null||!target.defender.shield||!target.defender.protect)return 1;
        double dx=sourceX-target.x,dz=sourceZ-target.z,length=Math.hypot(dx,dz);
        return length>1e-8&&(-Math.sin(target.yaw)*dx-Math.cos(target.yaw)*dz)/length>=.5?.7:1;
    }

    void updateDummy(Dummy dummy, double dt) {
        dummy.flash = Math.max(0, dummy.flash - dt);
        dummy.damageTime = Math.max(0, dummy.damageTime - dt);
        dummy.slowTime = Math.max(0, dummy.slowTime - dt);
        if (dummy.slowTime == 0) dummy.slowAmount = 0;
        if (!dummy.alive()) {
            dummy.respawnTime -= dt;
            if (dummy.respawnTime <= 0) {
                dummy.health = dummy.maxHealth;
                dummy.x = dummy.spawnX; dummy.z = dummy.spawnZ;
                dummy.vx = dummy.vz = dummy.slowTime = dummy.slowAmount = dummy.verticalSpeed = 0;dummy.lift=dummy.spawnY;
                effect(GameEffect.Kind.RESPAWN, dummy.x, 0, dummy.z, 1, 0);
            }
            return;
        }
        // Exact integration of exponential friction makes pushes independent of frame rate.
        double decay = Math.exp(-5 * dt), displacement = (1 - decay) / 5;
        double[] position=MapPhysics.move(map,dummy.x,dummy.lift,dummy.z,dummy.x+dummy.vx*displacement,dummy.lift,dummy.z+dummy.vz*displacement,.5,dummy.height());
        dummy.x=position[0];dummy.lift=position[1];dummy.z=position[2];
        dummy.vx *= decay; dummy.vz *= decay;
        if(!dummy.playerBody && dummy.lift>0) {
            double oldY=dummy.lift;
            dummy.lift+=dummy.verticalSpeed*dt-9*dt*dt;
            dummy.verticalSpeed-=18*dt;
            double resolved=MapPhysics.move(map,dummy.x,oldY,dummy.z,dummy.x,dummy.lift,dummy.z,.5,dummy.height())[1];
            if(resolved!=dummy.lift)dummy.verticalSpeed=0;dummy.lift=resolved;
        }
    }

    private Dummy inFront() {
        Dummy nearest = null;
        double best = Double.POSITIVE_INFINITY;
        Hitbox box=activeHitbox();
        for (Dummy dummy : dummies) {
            if (!dummy.alive() || !box.overlaps(dummy) || shield&&!shieldVisible(dummy)) continue;
            double dx = dummy.x - playerX, dz = dummy.z - playerZ;
            double distance = dx*dx+dz*dz+(dummy.lift-playerY)*(dummy.lift-playerY);
            if (distance < best) {
                nearest = dummy; best = distance;
            }
        }
        return nearest;
    }

    private Dummy sweptTarget(double ax, double az, double bx, double bz) {
        Dummy nearest = null;
        double best = Double.POSITIVE_INFINITY;
        Hitbox box=hitbox(Move.HAYMAKER,ax,playerY,az,facing);
        for (Dummy dummy : dummies) {
            if (!dummy.alive()) continue;
            double t=box.sweep(dummy,bx-ax,0,bz-az);
            if (Double.isFinite(t) && t < best) {
                nearest = dummy; best = t;
            }
        }
        return nearest;
    }

    private void hit(Dummy dummy, double baseDamage, double push) {
        double damage = baseDamage * attackMultiplier * frontalDamageFactor(dummy,playerX,playerZ);
        dummy.health = Math.max(0, dummy.health - damage);
        dummy.lastDamage = damage; dummy.damageTime = 1; dummy.flash = .18;
        dummy.vx = -Math.sin(facing) * push;
        dummy.vz = -Math.cos(facing) * push;
        effect(GameEffect.Kind.HIT, dummy.x, dummy.lift + .6, dummy.z, damage, active.ordinal());
        if (!dummy.alive()) {
            dummy.respawnTime = 4;
            if (!dummy.playerBody) effect(GameEffect.Kind.DEFEAT, dummy.x, dummy.lift, dummy.z, 1, 0);
        }
        // Every damaging hit grants a stack, including each individual Pummel hit.
        if(!shield){multiplier = Math.min(MAX_MULTIPLIER, Math.round((multiplier + MULTIPLIER_STEP) * 100) / 100.0);
        streakTime = STREAK_DURATION;}
    }

    private void finish(double recovery) {
        if (grabbed != null) grabbed.verticalSpeed = 0;
        grabbed = null;
        active = null;
        endLag = recovery;
    }

    Dummy grabbedTarget() { return grabbed; }

    void damagePlayer(double damage) {
        if (godMode || health <= 0 || !Double.isFinite(damage) || damage <= 0) return;
        health = Math.max(0, health - damage);
        if (health == 0) {
            effect(GameEffect.Kind.DEFEAT, playerX, playerY, playerZ, 1, 1);
            finish(0);
            multiplier = 1; streakTime = 0;
            tell("Defeated");
        }
    }

    void effect(GameEffect.Kind kind, double x, double y, double z, double strength, int variant) {
        if (effects.size() >= 128) effects.remove(0);
        effects.add(new GameEffect(kind, x, y, z, facing, strength, variant));
    }
    private void tell(String text) { message = text; messageTime = 2.5; }
}
