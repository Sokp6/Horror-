using UnityEngine;
using UnityEngine.AI;

/// <summary>Boss monster with phases and special attacks.</summary>
[RequireComponent(typeof(NavMeshAgent))]
public class BossAI : MonoBehaviour
{
    [Header("Stats")]
    public float maxHealth = 500f;
    private float health = 500f;
    public float damage = 40f;
    public float attackCooldown = 2f;
    public float detectionRange = 40f;

    [Header("Phases")]
    public float phase2Threshold = 0.6f; // 60% health
    public float phase3Threshold = 0.3f; // 30% health
    private int currentPhase = 1;

    [Header("References")]
    public Transform[] summonPoints;
    public GameObject summonPrefab;
    public GameObject projectilePrefab;
    public Transform projectileSpawn;
    public AudioClip roarSound;
    public AudioClip deathSound;

    private NavMeshAgent agent;
    private Transform player;
    private float attackTimer;
    private float specialTimer;
    private bool isDead;
    private Animator anim;

    void Start()
    {
        agent = GetComponent<NavMeshAgent>();
        anim = GetComponent<Animator>();
        player = Camera.main?.transform;
        health = maxHealth;
        agent.speed = 2.5f;
    }

    void Update()
    {
        if (isDead || GameManager.Instance?.isGameOver == true) return;

        float dist = player != null ? Vector3.Distance(transform.position, player.position) : 999f;
        if (attackTimer > 0) attackTimer -= Time.deltaTime;
        if (specialTimer > 0) specialTimer -= Time.deltaTime;

        // Phase transitions
        float hpRatio = health / maxHealth;
        if (hpRatio <= phase3Threshold && currentPhase < 3) { currentPhase = 3; PhaseTransition(3); }
        else if (hpRatio <= phase2Threshold && currentPhase < 2) { currentPhase = 2; PhaseTransition(2); }

        if (dist < detectionRange)
        {
            if (dist > 5f) Chase(dist);
            else Attack(dist);
        }
        else
        {
            agent.isStopped = true;
        }

        // Look at player
        if (player != null)
            transform.LookAt(new Vector3(player.position.x, transform.position.y, player.position.z));
    }

    void Chase(float dist)
    {
        agent.isStopped = false;
        agent.speed = currentPhase >= 3 ? 5f : (currentPhase >= 2 ? 3.5f : 2.5f);
        agent.SetDestination(player.position);

        // Special attacks
        if (specialTimer <= 0)
        {
            specialTimer = currentPhase >= 3 ? 4f : (currentPhase >= 2 ? 6f : 8f);

            if (currentPhase >= 3 && Random.value < 0.4f)
                SummonMinions();
            else if (currentPhase >= 2 && Random.value < 0.5f)
                ShootProjectile();
        }

        // Sanity drain
        if (dist < 15f && GameManager.Instance != null)
            GameManager.Instance.DrainSanity(5f * Time.deltaTime);
    }

    void Attack(float dist)
    {
        agent.isStopped = true;

        if (attackTimer <= 0)
        {
            attackTimer = attackCooldown;
            GameManager.Instance?.DamagePlayer(damage);

            // Knockback
            if (player != null)
            {
                Vector3 knockback = (player.position - transform.position).normalized * 5f;
                var cc = player.GetComponent<CharacterController>();
                if (cc != null) cc.Move(knockback * Time.deltaTime);
            }

            if (currentPhase >= 3 && Random.value < 0.5f) ShootProjectile();
        }
    }

    void ShootProjectile()
    {
        if (projectilePrefab == null || projectileSpawn == null || player == null) return;
        GameObject proj = Instantiate(projectilePrefab, projectileSpawn.position, Quaternion.identity);
        Vector3 dir = (player.position - projectileSpawn.position).normalized;
        proj.GetComponent<Rigidbody>().velocity = dir * 15f;
        proj.GetComponent<Projectile>().damage = 20f;
    }

    void SummonMinions()
    {
        if (summonPrefab == null || summonPoints == null) return;
        int count = currentPhase >= 3 ? 3 : 2;
        for (int i = 0; i < count && i < summonPoints.Length; i++)
        {
            Instantiate(summonPrefab, summonPoints[i].position, Quaternion.identity);
        }
    }

    void PhaseTransition(int phase)
    {
        if (roarSound != null) AudioSource.PlayClipAtPoint(roarSound, transform.position, 1f);
        agent.speed += 0.5f;
        attackCooldown *= 0.8f;
    }

    public void TakeDamage(float amount)
    {
        health -= amount;
        if (health <= 0 && !isDead) Die();
    }

    void Die()
    {
        isDead = true;
        if (deathSound != null) AudioSource.PlayClipAtPoint(deathSound, transform.position, 1f);
        agent.isStopped = true;
        GetComponent<Collider>().enabled = false;
        GameManager.Instance?.BossKilled();
        Destroy(gameObject, 3f);
    }
}

/// <summary>Boss projectile.</summary>
public class Projectile : MonoBehaviour
{
    public float damage = 20f;
    public float lifetime = 5f;
    public GameObject impactEffect;

    void Start() { Destroy(gameObject, lifetime); }

    void OnCollisionEnter(Collision collision)
    {
        var player = collision.collider.GetComponent<FPSController>();
        if (player != null) GameManager.Instance?.DamagePlayer(damage);

        if (impactEffect != null) Instantiate(impactEffect, transform.position, Quaternion.identity);
        Destroy(gameObject);
    }
}
