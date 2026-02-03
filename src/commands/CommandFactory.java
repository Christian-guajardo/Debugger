package commands;

@FunctionalInterface
public interface CommandFactory {
    Command create(String[] args) throws Exception;
}
