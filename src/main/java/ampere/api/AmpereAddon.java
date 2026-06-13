package ampere.api;

public abstract class AmpereAddon {

    public String name = "";

    public String authors = "";

    public int color = 0xFFFFFFFF;

    public abstract int apiVersion();

    public void onRegisterCategories() {}

    public abstract void onInitialize();

    public abstract String getPackage();
}
