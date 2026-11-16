import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Portable, bounded sequence of independent particle effects. */
final class ParticleSequence {
    static final class Step {
        String name;double wait;ParticleEffect effect;
        Step(String name,double wait,ParticleEffect effect){this.name=name;this.wait=wait;this.effect=effect;}
        public String toString(){return name+String.format(Locale.ROOT,"  (+%.2fs)",wait);}
    }
    static void validate(ParticleEffect root)throws IOException{
        if(root.sequence==null||root.sequence.isEmpty()||root.sequence.size()>16)throw new IOException("Timeline requires 1-16 effects.");
        if(!Double.isFinite(root.interval)||root.interval<.1||root.interval>5)throw new IOException("Invalid loop gap.");
        for(Step s:root.sequence){if(s.name==null||s.name.length()>64||!Double.isFinite(s.wait)||s.wait<0||s.wait>30||s.effect==null||s.effect.sequence!=null)throw new IOException("Invalid timeline step (delay must be 0-30 seconds; no nested timelines).");s.effect.validate();}
    }
    static double duration(ParticleEffect root){double start=0,end=0;for(Step s:root.sequence){start+=s.wait;end=Math.max(end,start+s.effect.emissionDuration+(s.effect.pattern==ParticleEffect.Pattern.SPARKLE?.35:0)+s.effect.lifetime*(1+s.effect.lifetimeVariation));}return end;}
    static void save(ParticleEffect root,Path path)throws IOException{
        validate(root);Path absolute=path.toAbsolutePath();Files.createDirectories(absolute.getParent());Path temp=Files.createTempFile(absolute.getParent(),"sequence-",".tmp"),child=Files.createTempFile("particle-step-",".pfx");
        try{Properties props=new Properties();props.setProperty("version","1");props.setProperty("count",""+root.sequence.size());props.setProperty("interval",""+root.interval);
            for(int i=0;i<root.sequence.size();i++){Step s=root.sequence.get(i);props.setProperty("name."+i,s.name);props.setProperty("wait."+i,""+s.wait);}
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(temp))){out.putNextEntry(new ZipEntry("sequence.properties"));props.store(out,"Particle timeline");out.closeEntry();long total=0;
                for(int i=0;i<root.sequence.size();i++){root.sequence.get(i).effect.save(child);total+=Files.size(child);if(total>64*1024*1024)throw new IOException("Timeline assets must total at most 64 MB.");out.putNextEntry(new ZipEntry("step-"+i+".pfx"));Files.copy(child,out);out.closeEntry();}}
            Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);Files.deleteIfExists(child);}
    }
    static ParticleEffect load(ZipFile zip)throws IOException{
        Properties props=new Properties();try(InputStream in=zip.getInputStream(zip.getEntry("sequence.properties"))){byte[] bytes=in.readNBytes(16385);if(bytes.length>16384)throw new IOException("Oversized timeline settings.");props.load(new ByteArrayInputStream(bytes));}
        ParticleEffect root=new ParticleEffect();root.sequence=new ArrayList<>();
        try{if(!"1".equals(props.getProperty("version")))throw new IOException("Unsupported timeline version.");int count=Integer.parseInt(props.getProperty("count"));if(count<1||count>16)throw new IOException("Invalid timeline count.");root.interval=Double.parseDouble(props.getProperty("interval"));
            Path child=Files.createTempFile("particle-step-",".pfx");long total=0;
            try{for(int i=0;i<count;i++){ZipEntry entry=zip.getEntry("step-"+i+".pfx");if(entry==null)throw new IOException("Missing timeline effect.");try(InputStream in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(17*1024*1024+1);total+=bytes.length;if(bytes.length>17*1024*1024||total>64*1024*1024)throw new IOException("Oversized timeline assets.");Files.write(child,bytes);}
                    try(ZipFile nested=new ZipFile(child.toFile())){if(nested.getEntry("sequence.properties")!=null)throw new IOException("Nested timelines are not supported.");}
                    root.sequence.add(new Step(props.getProperty("name."+i),Double.parseDouble(props.getProperty("wait."+i)),ParticleEffect.load(child)));}
            }finally{Files.deleteIfExists(child);}
            validate(root);return root;
        }catch(RuntimeException ex){throw new IOException("Invalid timeline settings.",ex);}
    }
}
