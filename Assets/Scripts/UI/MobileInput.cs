using UnityEngine;
using UnityEngine.UI;

/// <summary>Android touch controls: left joystick, right look area, fire button.</summary>
public class MobileInput : MonoBehaviour
{
    [Header("Joystick (Left)")]
    public RectTransform joystickBase;
    public RectTransform joystickStick;
    public float joystickMaxRadius = 100f;

    [Header("Look Area (Right)")]
    public RectTransform lookArea;

    [Header("Buttons")]
    public Button fireButton;
    public Button reloadButton;
    public Button jumpButton;
    public Button interactButton;

    private FPSController playerController;
    private WeaponBase currentWeapon;

    private int joystickFingerId = -1;
    private int lookFingerId = -1;
    private Vector2 joystickStartPos;
    private Vector2 moveOutput;
    private Vector2 lookOutput;

    void Start()
    {
        playerController = FindObjectOfType<FPSController>();
        currentWeapon = FindObjectOfType<WeaponBase>();

        if (playerController != null) playerController.useMobileInput = true;

        if (fireButton != null) fireButton.onClick.AddListener(OnFire);
        if (reloadButton != null) reloadButton.onClick.AddListener(OnReload);
        if (jumpButton != null) jumpButton.onClick.AddListener(OnJump);

        joystickStartPos = joystickBase != null ? joystickBase.position : Vector2.zero;
    }

    void Update()
    {
        HandleTouches();

        if (playerController != null)
        {
            playerController.SetMoveInput(moveOutput);
            playerController.SetLookInput(lookOutput);
        }
    }

    void HandleTouches()
    {
        moveOutput = Vector2.zero;
        lookOutput = Vector2.zero;

        for (int i = 0; i < Input.touchCount; i++)
        {
            Touch touch = Input.GetTouch(i);

            // Left half = joystick
            if (touch.position.x < Screen.width * 0.5f)
            {
                if (touch.phase == TouchPhase.Began && joystickFingerId == -1)
                {
                    joystickFingerId = touch.fingerId;
                    if (joystickBase != null) joystickBase.position = touch.position;
                }

                if (touch.fingerId == joystickFingerId)
                {
                    Vector2 dir = touch.position - (Vector2)joystickBase.position;
                    float dist = Mathf.Clamp(dir.magnitude, 0, joystickMaxRadius);
                    Vector2 clamped = dir.normalized * dist;

                    if (joystickStick != null)
                        joystickStick.position = (Vector2)joystickBase.position + clamped;

                    moveOutput = clamped / joystickMaxRadius;
                }

                if (touch.phase == TouchPhase.Ended && touch.fingerId == joystickFingerId)
                {
                    joystickFingerId = -1;
                    if (joystickStick != null) joystickStick.position = joystickBase.position;
                    moveOutput = Vector2.zero;
                }
            }
            // Right half = look
            else
            {
                if (touch.phase == TouchPhase.Began && lookFingerId == -1)
                    lookFingerId = touch.fingerId;

                if (touch.fingerId == lookFingerId && touch.phase == TouchPhase.Moved)
                {
                    lookOutput = touch.deltaPosition;
                }

                if (touch.phase == TouchPhase.Ended && touch.fingerId == lookFingerId)
                {
                    lookFingerId = -1;
                    lookOutput = Vector2.zero;
                }
            }
        }
    }

    void OnFire()
    {
        currentWeapon?.TryFire();
    }

    void OnReload()
    {
        currentWeapon?.Reload();
    }

    void OnJump()
    {
        playerController?.SetJump(true);
        Invoke(nameof(ResetJump), 0.2f);
    }

    void ResetJump()
    {
        playerController?.SetJump(false);
    }
}
