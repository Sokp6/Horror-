using UnityEngine;
using System.Collections;

/// <summary>Base weapon class for all guns.</summary>
public abstract class WeaponBase : MonoBehaviour
{
    public string weaponName = "Pistol";
    public float damage = 25f;
    public float range = 100f;
    public float fireRate = 0.2f;
    public int maxAmmo = 18;
    public int currentAmmo = 18;
    public bool isAutomatic = false;
    public float reloadTime = 1.5f;
    public GameObject muzzleFlash;
    public AudioClip fireSound;
    public AudioClip reloadSound;
    public Transform muzzlePoint;

    protected float nextFireTime = 0f;
    protected bool isReloading = false;
    protected Camera playerCam;
    protected Animator anim;

    public virtual void Start()
    {
        playerCam = Camera.main;
        anim = GetComponent<Animator>();
        if (muzzleFlash != null) muzzleFlash.SetActive(false);
    }

    public virtual bool TryFire()
    {
        if (Time.time < nextFireTime || isReloading) return false;
        if (currentAmmo <= 0) { Reload(); return false; }

        nextFireTime = Time.time + fireRate;
        currentAmmo--;
        Fire();
        return true;
    }

    protected virtual void Fire()
    {
        // Muzzle flash
        if (muzzleFlash != null) StartCoroutine(FlashMuzzle());

        // Play sound
        if (fireSound != null) AudioSource.PlayClipAtPoint(fireSound, transform.position, 0.7f);

        // Raycast
        RaycastHit hit;
        Vector3 dir = playerCam.transform.forward;
        // Add slight spread
        dir += Random.insideUnitSphere * 0.02f;

        if (Physics.Raycast(playerCam.transform.position, dir, out hit, range))
        {
            // Hit monster?
            MonsterAI monster = hit.collider.GetComponent<MonsterAI>();
            if (monster != null) monster.TakeDamage(damage);
            // Hit boss?
            BossAI boss = hit.collider.GetComponent<BossAI>();
            if (boss != null) boss.TakeDamage(damage);

            // Impact effect
            Debug.DrawRay(playerCam.transform.position, dir * hit.distance, Color.red, 0.1f);
        }

        // Camera shake
        ShakeCamera(0.1f, 0.3f);
    }

    protected IEnumerator FlashMuzzle()
    {
        muzzleFlash.SetActive(true);
        yield return new WaitForSeconds(0.05f);
        muzzleFlash.SetActive(false);
    }

    public virtual void Reload()
    {
        if (isReloading || currentAmmo == maxAmmo) return;
        StartCoroutine(ReloadRoutine());
    }

    protected virtual IEnumerator ReloadRoutine()
    {
        isReloading = true;
        if (reloadSound != null) AudioSource.PlayClipAtPoint(reloadSound, transform.position, 0.5f);
        yield return new WaitForSeconds(reloadTime);
        currentAmmo = maxAmmo;
        isReloading = false;
    }

    protected void ShakeCamera(float duration, float magnitude)
    {
        if (playerCam != null)
        {
            var shake = playerCam.GetComponent<CameraShake>();
            if (shake != null) shake.Shake(duration, magnitude);
        }
    }
}

/// <summary>Simple camera shake effect.</summary>
public class CameraShake : MonoBehaviour
{
    public void Shake(float duration, float magnitude)
    {
        StartCoroutine(DoShake(duration, magnitude));
    }

    IEnumerator DoShake(float duration, float magnitude)
    {
        Vector3 orig = transform.localPosition;
        float elapsed = 0f;
        while (elapsed < duration)
        {
            float x = Random.Range(-1f, 1f) * magnitude;
            float y = Random.Range(-1f, 1f) * magnitude;
            transform.localPosition = orig + new Vector3(x, y, 0);
            elapsed += Time.deltaTime;
            yield return null;
        }
        transform.localPosition = orig;
    }
}
