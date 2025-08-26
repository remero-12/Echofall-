import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {
    private long window;
    private final int WIDTH = 800;
    private final int HEIGHT = 600;

    private GameState currentState = GameState.START_SCREEN;
    private StartScreen startScreen;
    private GameWorld gameWorld;

    public void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_ANY_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Echofall", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // VSync
        GL.createCapabilities();
        org.lwjgl.opengl.GLUtil.setupDebugMessageCallback();

        glDisable(GL_DEPTH_TEST);
        System.out.println("OpenGL Version: " + GL11.glGetString(GL11.GL_VERSION));
        System.out.println("GL Renderer: " + GL11.glGetString(GL11.GL_RENDERER));
        System.out.println("GL Vendor:   " + GL11.glGetString(GL11.GL_VENDOR));

        glfwShowWindow(window);

        startScreen = new StartScreen();

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (currentState == GameState.START_SCREEN && key == GLFW_KEY_ENTER) {
                    currentState = GameState.PLAYING;
                    gameWorld = new GameWorld(WIDTH, HEIGHT, System.currentTimeMillis());
                } else if (currentState == GameState.PLAYING && key == GLFW_KEY_E) {
                    gameWorld.toggleInventory();
                }
            }
        });
        
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (currentState == GameState.PLAYING && action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT) {
                if (gameWorld.isInventoryOpen()) {
                    double[] mx = new double[1];
                    double[] my = new double[1];
                    glfwGetCursorPos(window, mx, my);
                    
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        var pW = stack.mallocInt(1);
                        var pH = stack.mallocInt(1);
                        glfwGetFramebufferSize(window, pW, pH);
                        int fbWidth = pW.get(0);
                        int fbHeight = pH.get(0);
                        
                        gameWorld.handleInventoryClick(mx[0], my[0], fbWidth, fbHeight);
                    }
                }
            }
        });
    }

    private void loop() {
        long lastTime = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            float dt = (now - lastTime) / 1_000_000_000f;
            if (dt > 0.05f) dt = 0.05f; // clamp
            lastTime = now;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                var pW = stack.mallocInt(1);
                var pH = stack.mallocInt(1);
                glfwGetFramebufferSize(window, pW, pH);
                int fbWidth = pW.get(0);
                int fbHeight = pH.get(0);

                if (fbWidth == 0 || fbHeight == 0) {
                    glfwWaitEvents();
                    continue;
                }

                glViewport(0, 0, fbWidth, fbHeight);
                glDrawBuffer(GL_BACK);

                if (currentState == GameState.START_SCREEN) {
                    startScreen.render(fbWidth, fbHeight);
                } else if (currentState == GameState.PLAYING) {
                    boolean left = glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS;
                    boolean right = glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS;
                    boolean jump = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS;
                    // Hotbar selection 1-9
                    for (int i = 0; i < 9; i++) {
                        if (glfwGetKey(window, GLFW_KEY_1 + i) == GLFW_PRESS) {
                            gameWorld.setHotbarSelected(i);
                        }
                    }
                    double[] mx = new double[1];
                    double[] my = new double[1];
                    glfwGetCursorPos(window, mx, my);
                    boolean mining = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS;
                    boolean placing = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_2) == GLFW_PRESS;
                    gameWorld.updateWithInput(dt, left, right, jump, mx[0], my[0], fbWidth, fbHeight, mining, placing);
                    gameWorld.render(fbWidth, fbHeight);
                    gameWorld.renderFullInventory(fbWidth, fbHeight);
                }
            }

            int err = glGetError();
            if (err != GL_NO_ERROR) {
                System.err.println("GL error: 0x" + Integer.toHexString(err));
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    public static void main(String[] args) {
        new Main().run();
    }
}

