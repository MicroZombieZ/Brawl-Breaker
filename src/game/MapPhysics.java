/** Shared solo/host map collision and lightweight loose-object physics. */
final class MapPhysics {
    static double[] boundary(MapDefinition map,double x,double z,double radius){
        double a=Math.max(.1,map.width/2-radius),b=Math.max(.1,map.depth/2-radius);
        x=Math.max(-a,Math.min(a,x));z=Math.max(-b,Math.min(b,z));
        if(map.plane==MapDefinition.Plane.ELLIPSE){double distance=Math.hypot(x/a,z/b);if(distance>1){x/=distance;z/=distance;}}
        return new double[]{x,z};
    }
    /** Axis-separated swept boxes; conservative bounds for curved/rotated shapes. */
    static double[] move(MapDefinition map,double ox,double oy,double oz,double x,double y,double z,double radius,double height){
        return move(map,ox,oy,oz,x,y,z,radius,height,null);
    }
    private static double[] move(MapDefinition map,double ox,double oy,double oz,double x,double y,double z,double radius,double height,MapDefinition.Part ignore){
        double[] edge=boundary(map,x,z,radius);x=edge[0];z=edge[1];
        // Resolve horizontal movement against the prior foot height, allowing jumps above obstacles.
        for(MapDefinition.Part p:map.objects)if(p!=ignore&&p.collision){double[] b=p.bounds();if(oy+height<=b[1]+1e-7||oy>=b[4]-1e-7)continue;
            if(oz+radius>b[2]&&oz-radius<b[5]){if(ox+radius<=b[0]+1e-7&&x+radius>b[0])x=b[0]-radius;else if(ox-radius>=b[3]-1e-7&&x-radius<b[3])x=b[3]+radius;}
        }
        for(MapDefinition.Part p:map.objects)if(p!=ignore&&p.collision){double[] b=p.bounds();if(oy+height<=b[1]+1e-7||oy>=b[4]-1e-7)continue;
            if(x+radius>b[0]&&x-radius<b[3]){if(oz+radius<=b[2]+1e-7&&z+radius>b[2])z=b[2]-radius;else if(oz-radius>=b[5]-1e-7&&z-radius<b[5])z=b[5]+radius;}
        }
        y=Math.max(0,y);
        for(MapDefinition.Part p:map.objects)if(p!=ignore&&p.collision){double[] b=p.bounds();if(x+radius<=b[0]||x-radius>=b[3]||z+radius<=b[2]||z-radius>=b[5])continue;
            if(oy>=b[4]-1e-6&&y<b[4])y=b[4];else if(oy+height<=b[1]+1e-6&&y+height>b[1])y=b[1]-height;
        }
        edge=boundary(map,x,z,radius);return new double[]{edge[0],Math.max(0,y),edge[1]};
    }
    static void tick(MapDefinition map,double dt){
        double remaining=Math.min(.1,Math.max(0,dt));
        while(remaining>1e-8){double step=Math.min(.01,remaining);remaining-=step;
            for(MapDefinition.Part p:map.objects)if(!p.anchored){
                p.vy-=18*step;double nx=p.x+p.vx*step,ny=p.y+p.vy*step,nz=p.z+p.vz*step;
                double[] b=move(map,p.x,p.y,p.z,nx,ny,nz,Math.max(p.w,p.d)/2,p.h,p);
                if(b[0]!=nx)p.vx*=-.2;if(b[2]!=nz)p.vz*=-.2;
                if(b[1]!=ny){p.vy=Math.abs(p.vy)>.8?-p.vy*.2:0;p.vx*=Math.exp(-7*step);p.vz*=Math.exp(-7*step);}
                p.x=b[0];p.y=Math.min(200,b[1]);p.z=b[2];
            }
        }
    }
}
