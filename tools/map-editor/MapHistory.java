import java.util.*;

/** Editor-only snapshots also support unfinished/temporarily invalid maps. */
final class MapHistory {
    final MapEditor editor;
    final Deque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    State current;boolean grouping;
    MapHistory(MapEditor editor){this.editor=editor;}
    static final class State {
        final MapDefinition map;final int[] selection;
        State(MapEditor e){map=copy(e.map);selection=e.selection.getSelectedIndices();}
    }
    static MapDefinition copy(MapDefinition source){MapDefinition m=new MapDefinition();m.name=source.name;m.width=source.width;m.depth=source.depth;m.plane=source.plane;m.color=source.color;for(MapDefinition.Part p:source.objects)m.objects.add(p.copy());for(MapDefinition.Spawn s:source.spawns)m.spawns.add(new MapDefinition.Spawn(s.x,s.y,s.z,s.team));for(MapDefinition.Spawn s:source.targets)m.targets.add(new MapDefinition.Spawn(s.x,s.y,s.z,s.team));return m;}
    static java.util.List<Object> values(MapDefinition m){java.util.List<Object> v=new ArrayList<>(Arrays.asList(m.name,m.width,m.depth,m.plane,m.color,m.objects.size(),m.spawns.size(),m.targets.size()));for(MapDefinition.Part p:m.objects)v.addAll(Arrays.asList(p.name,p.shape,p.x,p.y,p.z,p.w,p.h,p.d,p.yaw,p.vx,p.vy,p.vz,p.anchored,p.collision,p.color));for(java.util.List<MapDefinition.Spawn> list:Arrays.asList(m.spawns,m.targets))for(MapDefinition.Spawn s:list)v.addAll(Arrays.asList(s.x,s.y,s.z,s.team));return v;}
    void reset(){undo.clear();redo.clear();grouping=false;current=new State(editor);}
    void begin(){if(current==null)reset();if(!grouping)current=new State(editor);grouping=true;}
    void record(){if(current==null){reset();return;}if(grouping)return;State next=new State(editor);if(!values(next.map).equals(values(current.map))){undo.addLast(current);while(undo.size()>100)undo.removeFirst();redo.clear();current=next;}}
    void end(){if(!grouping)return;grouping=false;record();}
    void cancel(){grouping=false;current=new State(editor);}
    void travel(boolean forward){editor.gizmo.cancel();Deque<State> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return;to.addLast(new State(editor));State s=from.removeLast();editor.map=copy(s.map);editor.simulation=null;editor.refreshList(null);editor.selection.clearSelection();for(int index:s.selection)if(index<editor.items.size())editor.selection.addSelectionInterval(index,index);editor.dirty=true;current=new State(editor);editor.inspect();editor.view.repaint();editor.status.setText(forward?"Redone":"Undone");}
}
