public class Player {
    public float x;
    public float y;
    public float width;
    public float height;
    public float velocityX;
    public float velocityY;
    public boolean onGround;

    public Player(float startX, float startY, float width, float height) {
        this.x = startX;
        this.y = startY;
        this.width = width;
        this.height = height;
        this.velocityX = 0f;
        this.velocityY = 0f;
        this.onGround = false;
    }
}


