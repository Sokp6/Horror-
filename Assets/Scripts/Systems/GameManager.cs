using UnityEngine;
using UnityEngine.SceneManagement;

/// <summary>Central game state manager. Persists across scenes.</summary>
public class GameManager : MonoBehaviour
{
    public static GameManager Instance { get; private set; }

    [Header("Player State")]
    public float playerHealth = 100f;
    public float maxHealth = 100f;
    public float playerSanity = 100f;
    public float maxSanity = 100f;
    public int pistolAmmo = 18;
    public int shotgunAmmo = 4;
    public bool hasShotgun = false;
    public bool hasFlashlight = true;
    public float flashlightBattery = 100f;
    public int keysFound = 0;
    public int documentsFound = 0;
    public int totalDocuments = 8;

    [Header("Game State")]
    public bool isGameOver = false;
    public bool isVictory = false;
    public string gameOverMessage = "";
    public bool isPaused = false;

    void Awake()
    {
        if (Instance != null && Instance != this) { Destroy(gameObject); return; }
        Instance = this;
        DontDestroyOnLoad(gameObject);
    }

    public void DamagePlayer(float damage)
    {
        if (isGameOver) return;
        playerHealth = Mathf.Max(0, playerHealth - damage);
        if (playerHealth <= 0) GameOver("ВЫ ПОГИБЛИ", false);
    }

    public void HealPlayer(float amount)
    {
        playerHealth = Mathf.Min(maxHealth, playerHealth + amount);
    }

    public void DrainSanity(float amount)
    {
        if (isGameOver) return;
        playerSanity = Mathf.Max(0, playerSanity - amount);
        if (playerSanity <= 0) GameOver("РАССУДОК УТРАЧЕН", false);
    }

    public void RestoreSanity(float amount)
    {
        playerSanity = Mathf.Min(maxSanity, playerSanity + amount);
    }

    public void AddAmmo(int pistol, int shotgun)
    {
        pistolAmmo += pistol;
        shotgunAmmo += shotgun;
    }

    public void GameOver(string message, bool victory)
    {
        isGameOver = true;
        isVictory = victory;
        gameOverMessage = message;
        Cursor.lockState = CursorLockMode.None;
        Cursor.visible = true;
    }

    public void BossKilled()
    {
        GameOver("БОСС УНИЧТОЖЕН!\nВЫ ВЫБРАЛИСЬ!", true);
    }
}
