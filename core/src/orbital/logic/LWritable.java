package orbital.logic;

public interface LWritable{
    boolean writable(LExecutor exec);
    void write(LVar position, LVar value);
}
