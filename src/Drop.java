public class Drop {
    public float x;
    public float y;
    public float vx;
    public float vy;
    public int tileId; // 1 dirt, 3 trunk, 5 grass -> dirt, 6 stone, etc
    public boolean picked;

    public Drop(float x, float y, int tileId) {
        this.x = x;
        this.y = y;
        this.vx = (float)(Math.random() * 60 - 30);
        this.vy = (float)(-80 - Math.random() * 60);
        this.tileId = tileId;
        this.picked = false;
    }
}
