import static org.lwjgl.opengl.GL11.*;

public class StartScreen {
    public void render(int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glClearColor(0.05f, 0.05f, 0.08f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glColor3f(1f, 1f, 1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(10, 10);
        glVertex2f(width - 10, 10);
        glVertex2f(width - 10, height - 10);
        glVertex2f(10, height - 10);
        glEnd();
    }
}

