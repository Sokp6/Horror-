using UnityEditor;
using UnityEngine;
using System.IO;

/// <summary>Build script for creating APK from Unity Editor menu.</summary>
public class BuildScript
{
    [MenuItem("Horror Game/Build Android APK (Debug)")]
    public static void BuildAndroidDebug()
    {
        BuildAndroid(BuildOptions.Development | BuildOptions.AllowDebugging);
    }

    [MenuItem("Horror Game/Build Android APK (Release)")]
    public static void BuildAndroidRelease()
    {
        BuildAndroid(BuildOptions.None);
    }

    static void BuildAndroid(BuildOptions options)
    {
        // Ensure output directory
        string dir = "Builds/Android";
        if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);

        // Set player settings
        PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android, ScriptingImplementation.IL2CPP);
        PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64 | AndroidArchitecture.ARMv7;

        // Build scenes
        string[] scenes = new string[EditorBuildSettings.scenes.Length];
        for (int i = 0; i < scenes.Length; i++)
            scenes[i] = EditorBuildSettings.scenes[i].path;

        string outputPath = $"{dir}/HorrorAwakening.apk";

        BuildReport report = BuildPipeline.BuildPlayer(
            scenes,
            outputPath,
            BuildTarget.Android,
            options);

        if (report.summary.result == BuildResult.Succeeded)
        {
            Debug.Log($"APK built successfully: {outputPath}");
            EditorUtility.RevealInFinder(outputPath);
        }
        else
        {
            Debug.LogError($"Build FAILED: {report.summary.result}");
        }
    }

    [MenuItem("Horror Game/Build Windows")]
    public static void BuildWindows()
    {
        string dir = "Builds/Windows";
        if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);

        string[] scenes = new string[EditorBuildSettings.scenes.Length];
        for (int i = 0; i < scenes.Length; i++)
            scenes[i] = EditorBuildSettings.scenes[i].path;

        BuildPipeline.BuildPlayer(scenes, $"{dir}/HorrorAwakening.exe",
            BuildTarget.StandaloneWindows64, BuildOptions.None);
        Debug.Log("Windows build complete!");
    }
}
