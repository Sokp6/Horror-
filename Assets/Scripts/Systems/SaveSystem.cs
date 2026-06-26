using UnityEngine;
using System;
using System.IO;

/// <summary>Save/load game state as JSON.</summary>
public class SaveSystem : MonoBehaviour
{
    public static SaveSystem Instance { get; private set; }

    [Serializable]
    public class SaveData
    {
        public float playerHealth;
        public float playerSanity;
        public int pistolAmmo;
        public int shotgunAmmo;
        public bool hasShotgun;
        public bool hasFlashlight;
        public float flashlightBattery;
        public int keysFound;
        public int documentsFound;
        public float posX, posY, posZ;
        public float rotY;
        public bool[] bossesKilled;
    }

    void Awake()
    {
        if (Instance != null && Instance != this) { Destroy(gameObject); return; }
        Instance = this;
        DontDestroyOnLoad(gameObject);
    }

    public void Save()
    {
        var gm = GameManager.Instance;
        var player = FindObjectOfType<FPSController>();
        if (gm == null || player == null) return;

        SaveData data = new SaveData
        {
            playerHealth = gm.playerHealth,
            playerSanity = gm.playerSanity,
            pistolAmmo = gm.pistolAmmo,
            shotgunAmmo = gm.shotgunAmmo,
            hasShotgun = gm.hasShotgun,
            hasFlashlight = gm.hasFlashlight,
            flashlightBattery = gm.flashlightBattery,
            keysFound = gm.keysFound,
            documentsFound = gm.documentsFound,
            posX = player.transform.position.x,
            posY = player.transform.position.y,
            posZ = player.transform.position.z,
            rotY = player.transform.eulerAngles.y
        };

        string json = JsonUtility.ToJson(data, true);
        string path = Path.Combine(Application.persistentDataPath, "horror_save.json");
        File.WriteAllText(path, json);
        Debug.Log("Game saved to " + path);
    }

    public SaveData Load()
    {
        string path = Path.Combine(Application.persistentDataPath, "horror_save.json");
        if (!File.Exists(path)) return null;

        string json = File.ReadAllText(path);
        return JsonUtility.FromJson<SaveData>(json);
    }

    public bool HasSave()
    {
        string path = Path.Combine(Application.persistentDataPath, "horror_save.json");
        return File.Exists(path);
    }

    public void ApplyLoad(SaveData data)
    {
        if (data == null) return;

        var gm = GameManager.Instance;
        var player = FindObjectOfType<FPSController>();
        if (gm == null || player == null) return;

        gm.playerHealth = data.playerHealth;
        gm.playerSanity = data.playerSanity;
        gm.pistolAmmo = data.pistolAmmo;
        gm.shotgunAmmo = data.shotgunAmmo;
        gm.hasShotgun = data.hasShotgun;
        gm.hasFlashlight = data.hasFlashlight;
        gm.flashlightBattery = data.flashlightBattery;
        gm.keysFound = data.keysFound;
        gm.documentsFound = data.documentsFound;

        var cc = player.GetComponent<CharacterController>();
        if (cc != null) cc.enabled = false;
        player.transform.position = new Vector3(data.posX, data.posY, data.posZ);
        player.transform.eulerAngles = new Vector3(0, data.rotY, 0);
        if (cc != null) cc.enabled = true;
    }

    public void DeleteSave()
    {
        string path = Path.Combine(Application.persistentDataPath, "horror_save.json");
        if (File.Exists(path)) File.Delete(path);
    }
}
