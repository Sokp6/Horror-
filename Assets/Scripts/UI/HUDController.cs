using UnityEngine;
using UnityEngine.UI;

/// <summary>In-game HUD: health, sanity, ammo, crosshair, messages.</summary>
public class HUDController : MonoBehaviour
{
    [Header("Health")]
    public Slider healthSlider;
    public Image healthFill;
    public Text healthText;
    public Color healthHigh = Color.green;
    public Color healthMid = Color.yellow;
    public Color healthLow = Color.red;

    [Header("Sanity")]
    public Slider sanitySlider;
    public Image sanityFill;
    public Text sanityText;

    [Header("Ammo")]
    public Text ammoText;
    public Text shotgunAmmoText;
    public GameObject shotgunHUD;

    [Header("Other")]
    public Text batteryText;
    public Text keysText;
    public Text messageText;
    public float messageDisplayTime = 3f;
    private float messageTimer;

    [Header("Crosshair")]
    public Image crosshair;
    public Color crosshairNormal = Color.white;
    public Color crosshairEnemy = Color.red;

    [Header("Damage Vignette")]
    public Image damageVignette;
    public float vignetteFadeSpeed = 3f;

    private GameManager gm;

    void Start()
    {
        gm = GameManager.Instance;
        if (damageVignette != null) damageVignette.color = Color.clear;
    }

    void Update()
    {
        if (gm == null) return;

        // Health
        float hpPct = gm.playerHealth / gm.maxHealth;
        if (healthSlider != null) healthSlider.value = hpPct;
        if (healthText != null) healthText.text = $"HP {Mathf.CeilToInt(gm.playerHealth)}%";
        if (healthFill != null)
        {
            healthFill.color = hpPct > 0.6f ? healthHigh : (hpPct > 0.3f ? healthMid : healthLow);
        }

        // Sanity
        float spPct = gm.playerSanity / gm.maxSanity;
        if (sanitySlider != null) sanitySlider.value = spPct;
        if (sanityText != null) sanityText.text = $"SAN {Mathf.CeilToInt(gm.playerSanity)}%";

        // Ammo
        if (ammoText != null) ammoText.text = $"🔫 {gm.pistolAmmo}";
        if (shotgunHUD != null) shotgunHUD.SetActive(gm.hasShotgun);
        if (shotgunAmmoText != null) shotgunAmmoText.text = $"💥 {gm.shotgunAmmo}";

        // Battery
        if (batteryText != null) batteryText.text = gm.hasFlashlight ? $"🔦 {Mathf.CeilToInt(gm.flashlightBattery)}%" : "🔦 0%";
        if (batteryText != null) batteryText.color = gm.flashlightBattery > 25f ? Color.white : new Color(1f, 0.8f, 0.3f);

        // Keys
        if (keysText != null) keysText.text = $"🔑 {gm.keysFound}";

        // Message
        if (messageTimer > 0)
        {
            messageTimer -= Time.deltaTime;
            if (messageTimer <= 0 && messageText != null) messageText.text = "";
        }

        // Damage vignette
        if (damageVignette != null)
        {
            float targetAlpha = gm.playerHealth < 30f ? 0.5f : 0f;
            Color c = damageVignette.color;
            c.a = Mathf.Lerp(c.a, targetAlpha, vignetteFadeSpeed * Time.deltaTime);
            damageVignette.color = c;
        }

        // Game over
        if (gm.isGameOver && messageText != null)
        {
            messageText.text = gm.gameOverMessage;
            messageText.color = gm.isVictory ? Color.green : Color.red;
            messageText.fontSize = 48;
        }
    }

    public void ShowMessage(string msg)
    {
        if (messageText != null)
        {
            messageText.text = msg;
            messageText.color = Color.white;
            messageText.fontSize = 28;
        }
        messageTimer = messageDisplayTime;
    }
}
