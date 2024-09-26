package net.mslivo.example;


import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import net.mslivo.core.engine.tools.Tools;

public class ExampleLauncherMain {

    public static void main(String[] args) {

        Tools.App.launch(
                new ExampleMain(),
                ExampleMainConstants.APP_TITLE,
                ExampleMainConstants.INTERNAL_RESOLUTION_WIDTH,
                ExampleMainConstants.INTERNAL_RESOLUTION_HEIGHT, null,0, false);


    }
}
