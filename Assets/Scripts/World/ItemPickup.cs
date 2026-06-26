using UnityEngine;

/// <summary>Collectible items in the world.</summary>
public class ItemPickup : MonoBehaviour
{
    public enum ItemType { Medkit, AmmoPistol, AmmoShotgun, Shotgun, Key, Document, Battery, Adrenaline }

    public ItemType itemType = ItemType.Medkit;
    public float amount = 30f;
    public float rotateSpeed = 50f;
    public float bobSpeed = 2f;
    public float bobAmount = 0.2f;
    public AudioClip pickupSound;
    public GameObject pickupEffect;

    private Vector3 startPos;

    void Start() { startPos = transform.position; }

    void Update()
    {
        // Rotate and bob
        transform.Rotate(Vector3.up, rotateSpeed * Time.deltaTime, Space.World);
        transform.position = startPos + Vector3.up * Mathf.Sin(Time.time * bobSpeed) * bobAmount;
    }

    void OnTriggerEnter(Collider other)
    {
        if (!other.CompareTag("Player")) return;

        var gm = GameManager.Instance;
        if (gm == null) return;

        switch (itemType)
        {
            case ItemType.Medkit:
                gm.HealPlayer(amount);
                break;
            case ItemType.AmmoPistol:
                gm.AddAmmo(6, 0);
                break;
            case ItemType.AmmoShotgun:
                gm.AddAmmo(0, 4);
                break;
            case ItemType.Shotgun:
                gm.hasShotgun = true;
                gm.AddAmmo(0, 4);
                break;
            case ItemType.Key:
                gm.keysFound++;
                break;
            case ItemType.Document:
                gm.documentsFound++;
                break;
            case ItemType.Battery:
                gm.flashlightBattery = Mathf.Min(100f, gm.flashlightBattery + 40f);
                gm.hasFlashlight = true;
                break;
            case ItemType.Adrenaline:
                gm.HealPlayer(30f);
                gm.RestoreSanity(40f);
                break;
        }

        if (pickupSound != null) AudioSource.PlayClipAtPoint(pickupSound, transform.position, 0.6f);
        if (pickupEffect != null) Instantiate(pickupEffect, transform.position, Quaternion.identity);

        Destroy(gameObject);
    }
}

/// <summary>Door that opens when player approaches with a key.</summary>
public class DoorController : MonoBehaviour
{
    public bool isLocked = false;
    public bool requiresKey = true;
    public float openAngle = 90f;
    public float openSpeed = 3f;
    public AudioClip openSound;
    public AudioClip lockedSound;

    private bool isOpen = false;
    private Quaternion closedRotation;
    private Quaternion openRotation;
    private bool playedSound = false;

    void Start()
    {
        closedRotation = transform.rotation;
        openRotation = closedRotation * Quaternion.Euler(0, openAngle, 0);
    }

    void Update()
    {
        Quaternion target = isOpen ? openRotation : closedRotation;
        transform.rotation = Quaternion.Slerp(transform.rotation, target, openSpeed * Time.deltaTime);
    }

    void OnTriggerEnter(Collider other)
    {
        if (!other.CompareTag("Player") || isOpen) return;

        if (isLocked && requiresKey)
        {
            var gm = GameManager.Instance;
            if (gm != null && gm.keysFound > 0)
            {
                gm.keysFound--;
                isLocked = false;
                Open();
            }
            else
            {
                if (lockedSound != null && !playedSound)
                {
                    AudioSource.PlayClipAtPoint(lockedSound, transform.position, 0.5f);
                    playedSound = true;
                    Invoke(nameof(ResetSound), 2f);
                }
            }
        }
        else
        {
            Open();
        }
    }

    void Open()
    {
        isOpen = true;
        if (openSound != null) AudioSource.PlayClipAtPoint(openSound, transform.position, 0.5f);
        Invoke(nameof(Close), 5f);
    }

    void Close()
    {
        isOpen = false;
    }

    void ResetSound() { playedSound = false; }
}
