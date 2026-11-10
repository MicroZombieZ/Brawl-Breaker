import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/** Screen-space handles backed by world/local transform axes. */
final class MapGizmo {
    final MapEditor editor;final java.util.List<Handle> handles=new ArrayList<>();
    Handle active;Object target;double[] before;boolean wasDirty;
    final java.util.Map<Object,double[]> originals=new LinkedHashMap<>();
    MapGizmo(MapEditor editor){this.editor=editor;}
    static final class Handle {Shape hit;int axis;double dx,dy;Handle(Shape h,int a,double x,double y){hit=h;axis=a;dx=x;dy=y;}}
    static double limit(double v,double min,double max){return Math.max(min,Math.min(max,v));}
    double[] pivot(){java.util.List<Object> selected=editor.selectedItems();if(selected.isEmpty())return null;double[] center=new double[3];for(Object o:selected){if(o instanceof MapDefinition.Part){MapDefinition.Part p=(MapDefinition.Part)o;center[0]+=p.x;center[1]+=p.y+p.h/2;center[2]+=p.z;}else{MapDefinition.Spawn p=(MapDefinition.Spawn)o;center[0]+=p.x;center[1]+=p.y+.3;center[2]+=p.z;}}for(int i=0;i<3;i++)center[i]/=selected.size();return center;}
    double[] screen(double[] v){double[] c=editor.view.camera(v);if(c[2]<.1)return null;double k=Math.min(editor.view.getWidth(),editor.view.getHeight())*.9/c[2];return new double[]{editor.view.getWidth()/2.0+c[0]*k,editor.view.getHeight()/2.0-c[1]*k};}
    void begin(Object o){editor.history.begin();target=o;wasDirty=editor.dirty;originals.clear();for(Object item:editor.selectedItems()){double[] data;if(item instanceof MapDefinition.Part){MapDefinition.Part p=(MapDefinition.Part)item;data=new double[]{p.x,p.y,p.z,p.w,p.h,p.d,p.yaw};}else{MapDefinition.Spawn p=(MapDefinition.Spawn)item;data=new double[]{p.x,p.y,p.z};}originals.put(item,data);}before=originals.get(o);}
    boolean pick(Point point){if(editor.simulation!=null)return false;for(Handle h:handles)if(h.hit.contains(point)){active=h;begin(editor.selection.getSelectedValue());return true;}return false;}
    void finish(){editor.history.end();active=null;target=null;before=null;originals.clear();}
    void cancel(){if(!originals.isEmpty()){for(java.util.Map.Entry<Object,double[]> entry:originals.entrySet()){Object o=entry.getKey();double[] v=entry.getValue();if(o instanceof MapDefinition.Part){MapDefinition.Part p=(MapDefinition.Part)o;p.x=v[0];p.y=v[1];p.z=v[2];p.w=v[3];p.h=v[4];p.d=v[5];p.yaw=v[6];}else{MapDefinition.Spawn p=(MapDefinition.Spawn)o;p.x=v[0];p.y=v[1];p.z=v[2];}}editor.dirty=wasDirty;editor.inspect();}editor.history.cancel();finish();editor.view.cancelBox();editor.view.last=null;editor.view.moving=null;editor.view.repaint();}
    void translateXZ(double x,double z){for(Object o:originals.keySet())if(o instanceof MapDefinition.Part){MapDefinition.Part p=(MapDefinition.Part)o;p.x=limit(p.x+x,-200,200);p.z=limit(p.z+z,-200,200);}else{MapDefinition.Spawn p=(MapDefinition.Spawn)o;p.x=limit(p.x+x,-200,200);p.z=limit(p.z+z,-200,200);}editor.changed();}
    void drag(double dx,double dy,boolean precision){if(active==null||target==null)return;double factor=precision?.15:1;double amount=(dx*active.dx+dy*active.dy)/Math.max(.0001,active.dx*active.dx+active.dy*active.dy)*factor;int axis=active.axis;
        Object primary=target;for(Object item:originals.keySet()){target=item;
        if(target instanceof MapDefinition.Part){MapDefinition.Part p=(MapDefinition.Part)target;switch(editor.tool){
            case ROTATE:p.yaw=Math.IEEEremainder(p.yaw+dx*factor,360);break;
            case SCALE:double scale=Math.exp((dx-dy)*.008*factor);scale=limit(scale,.1/Math.min(p.w,Math.min(p.h,p.d)),100/Math.max(p.w,Math.max(p.h,p.d)));p.w*=scale;p.h*=scale;p.d*=scale;break;
            case RESIZE:if(axis==0)p.w=limit(p.w+amount,.1,100);if(axis==1)p.h=limit(p.h+amount,.1,100);if(axis==2)p.d=limit(p.d+amount,.1,100);break;
            case MOVE:if(axis==0)p.x=limit(p.x+amount,-200,200);if(axis==1)p.y=limit(p.y+amount,0,200);if(axis==2)p.z=limit(p.z+amount,-200,200);break;
            default:return;
        }}else if(editor.tool==MapEditor.Tool.MOVE){MapDefinition.Spawn s=(MapDefinition.Spawn)target;if(axis==0)s.x=limit(s.x+amount,-200,200);if(axis==1)s.y=limit(s.y+amount,0,200);if(axis==2)s.z=limit(s.z+amount,-200,200);} }target=primary;editor.changed();
    }
    void paint(Graphics2D g){handles.clear();double[] p=pivot();MapEditor.Tool tool=editor.tool;if(p==null||tool==MapEditor.Tool.SELECT||editor.simulation!=null)return;double[] origin=screen(p);if(origin==null)return;Object selected=editor.selection.getSelectedValue();if(!(selected instanceof MapDefinition.Part)&&tool!=MapEditor.Tool.MOVE)return;
        g.setStroke(new BasicStroke(3));
        if(tool==MapEditor.Tool.ROTATE){Ellipse2D ring=new Ellipse2D.Double(origin[0]-58,origin[1]-58,116,116);g.setColor(new Color(100,235,130));g.draw(ring);handles.add(new Handle(new BasicStroke(14).createStrokedShape(ring),1,1,0));g.drawString("Y rotation: drag left/right",(int)origin[0]-70,(int)origin[1]-65);return;}
        if(tool==MapEditor.Tool.SCALE){Rectangle2D box=new Rectangle2D.Double(origin[0]-9,origin[1]-9,18,18);g.setColor(Color.WHITE);g.fill(box);handles.add(new Handle(box,3,1,0));g.drawString("Uniform scale",(int)origin[0]+14,(int)origin[1]);return;}
        Color[] colors={new Color(255,100,100),new Color(100,235,130),new Color(100,170,255)};String[] names=tool==MapEditor.Tool.RESIZE?new String[]{"Width","Height","Depth"}:new String[]{"X","Y","Z"};
        for(int axis=0;axis<3;axis++){double[] tip=p.clone(),direction={0,0,0};direction[axis]=1;if(tool==MapEditor.Tool.RESIZE&&axis!=1){double a=Math.toRadians(((MapDefinition.Part)selected).yaw);direction[0]=axis==0?Math.cos(a):Math.sin(a);direction[2]=axis==0?-Math.sin(a):Math.cos(a);}for(int i=0;i<3;i++)tip[i]+=direction[i];double[] unit=screen(tip);if(unit==null)continue;double dx=unit[0]-origin[0],dy=unit[1]-origin[1],len=Math.hypot(dx,dy);if(len<.5)continue;double ex=origin[0]+dx/len*82,ey=origin[1]+dy/len*82;Line2D line=new Line2D.Double(origin[0],origin[1],ex,ey);g.setColor(colors[axis]);g.draw(line);g.fill(new Rectangle2D.Double(ex-5,ey-5,10,10));g.drawString(names[axis],(int)ex+7,(int)ey);handles.add(new Handle(new BasicStroke(13).createStrokedShape(line),axis,dx,dy));}
    }
}
