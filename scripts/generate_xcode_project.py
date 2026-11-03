#!/usr/bin/env python3
"""Generate a deterministic Xcode project without XcodeGen or external dependencies."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
IOS = ROOT / "iOS"
PROJECT = IOS / "HerMorse.xcodeproj"
objects = {}


class Ref(str):
    pass


def ident(name):
    return Ref(hashlib.sha256(name.encode()).hexdigest()[:24].upper())


def obj(key, isa, **fields):
    ref = ident(key)
    objects[ref] = dict(isa=Ref(isa), **fields)
    return ref


def encode(value, indent=0):
    tab = "\t" * indent
    if isinstance(value, Ref):
        return str(value)
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, int):
        return str(value)
    if isinstance(value, list):
        return "(\n" + "".join(tab + "\t" + encode(v, indent + 1) + ",\n" for v in value) + tab + ")"
    if isinstance(value, dict):
        return "{\n" + "".join(tab + "\t" + encode(k) + " = " + encode(v, indent + 1) + ";\n" for k, v in value.items()) + tab + "}"
    raise TypeError(value)


source_refs, source_builds, resource_builds = [], [], []
files = sorted((IOS / "HerMorse").glob("*.swift"))
for file in files:
    file_ref = obj("file:" + file.name, "PBXFileReference", lastKnownFileType="sourcecode.swift", path=file.name, sourceTree="<group>")
    source_refs.append(file_ref)
    source_builds.append(obj("build:" + file.name, "PBXBuildFile", fileRef=file_ref))
for name, kind in [("Assets.xcassets", "folder.assetcatalog"), ("PrivacyInfo.xcprivacy", "text.xml"), ("Info.plist", "text.plist.xml")]:
    file_ref = obj("file:" + name, "PBXFileReference", lastKnownFileType=kind, path=name, sourceTree="<group>")
    source_refs.append(file_ref)
    if name != "Info.plist":
        resource_builds.append(obj("build:" + name, "PBXBuildFile", fileRef=file_ref))

package = obj("package", "XCLocalSwiftPackageReference", relativePath="..")
product = obj("packageproduct", "XCSwiftPackageProductDependency", package=package, productName="HerMorseCore")
product_build = obj("packagebuild", "PBXBuildFile", productRef=product)
source_phase = obj("sources", "PBXSourcesBuildPhase", buildActionMask=2147483647, files=source_builds, runOnlyForDeploymentPostprocessing=0)
resource_phase = obj("resources", "PBXResourcesBuildPhase", buildActionMask=2147483647, files=resource_builds, runOnlyForDeploymentPostprocessing=0)
framework_phase = obj("frameworks", "PBXFrameworksBuildPhase", buildActionMask=2147483647, files=[product_build], runOnlyForDeploymentPostprocessing=0)
app_ref = obj("app", "PBXFileReference", explicitFileType="wrapper.application", includeInIndex=0, path="HerMorse.app", sourceTree="BUILT_PRODUCTS_DIR")
app_group = obj("appgroup", "PBXGroup", children=source_refs, path="HerMorse", sourceTree="<group>")
products_group = obj("products", "PBXGroup", children=[app_ref], name="Products", sourceTree="<group>")
main_group = obj("main", "PBXGroup", children=[app_group, products_group], sourceTree="<group>")

project_configs, target_configs = [], []
for config in ["Debug", "Release"]:
    project_configs.append(obj("projectconfig:" + config, "XCBuildConfiguration", name=config, buildSettings={
        "CLANG_ENABLE_MODULES": "YES", "IPHONEOS_DEPLOYMENT_TARGET": "17.0", "SDKROOT": "iphoneos",
        "SWIFT_VERSION": "5.0", "SWIFT_OPTIMIZATION_LEVEL": "-Onone" if config == "Debug" else "-O",
        "DEBUG_INFORMATION_FORMAT": "dwarf" if config == "Debug" else "dwarf-with-dsym",
        "SWIFT_ACTIVE_COMPILATION_CONDITIONS": "DEBUG $(inherited)" if config == "Debug" else "$(inherited)",
        "ENABLE_USER_SCRIPT_SANDBOXING": "YES", "CLANG_WARN_DOCUMENTATION_COMMENTS": "YES",
    }))
    target_configs.append(obj("targetconfig:" + config, "XCBuildConfiguration", name=config, buildSettings={
        "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon", "CODE_SIGN_STYLE": "Automatic",
        "CURRENT_PROJECT_VERSION": "1", "GENERATE_INFOPLIST_FILE": "NO", "INFOPLIST_FILE": "HerMorse/Info.plist",
        "LD_RUNPATH_SEARCH_PATHS": ["$(inherited)", "@executable_path/Frameworks"],
        "MARKETING_VERSION": "0.4.0", "PRODUCT_BUNDLE_IDENTIFIER": "fr.hermorse.app.ios",
        "PRODUCT_NAME": "$(TARGET_NAME)", "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator",
        "SUPPORTS_MACCATALYST": "NO", "TARGETED_DEVICE_FAMILY": "1,2",
        "SWIFT_STRICT_CONCURRENCY": "targeted", "SWIFT_EMIT_LOC_STRINGS": "YES",
    }))
project_config_list = obj("projectconfiglist", "XCConfigurationList", buildConfigurations=project_configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")
target_config_list = obj("targetconfiglist", "XCConfigurationList", buildConfigurations=target_configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")
target = obj("target", "PBXNativeTarget", buildConfigurationList=target_config_list,
             buildPhases=[source_phase, framework_phase, resource_phase], buildRules=[], dependencies=[],
             name="HerMorse", packageProductDependencies=[product], productName="HerMorse", productReference=app_ref,
             productType="com.apple.product-type.application")
project = obj("project", "PBXProject", attributes={"BuildIndependentTargetsInParallel": "YES", "LastUpgradeCheck": "1600"},
              buildConfigurationList=project_config_list, compatibilityVersion="Xcode 14.0", developmentRegion="fr",
              hasScannedForEncodings=0, knownRegions=["fr", "en", "Base"], mainGroup=main_group,
              packageReferences=[package], productRefGroup=products_group, projectDirPath="", projectRoot="", targets=[target])
PROJECT.mkdir(exist_ok=True)
content = {"archiveVersion": 1, "classes": {}, "objectVersion": 56, "objects": objects, "rootObject": project}
(PROJECT / "project.pbxproj").write_text("// !$*UTF8*$!\n" + encode(content) + "\n")
scheme_dir = PROJECT / "xcshareddata/xcschemes"
scheme_dir.mkdir(parents=True, exist_ok=True)
(scheme_dir / "HerMorse.xcscheme").write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES">
    <BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">
      <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="HerMorse.app" BlueprintName="HerMorse" ReferencedContainer="container:HerMorse.xcodeproj"/>
    </BuildActionEntry></BuildActionEntries>
  </BuildAction>
  <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables/></TestAction>
  <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES">
    <BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="HerMorse.app" BlueprintName="HerMorse" ReferencedContainer="container:HerMorse.xcodeproj"/></BuildableProductRunnable>
  </LaunchAction>
  <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES">
    <BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="HerMorse.app" BlueprintName="HerMorse" ReferencedContainer="container:HerMorse.xcodeproj"/></BuildableProductRunnable>
  </ProfileAction>
  <AnalyzeAction buildConfiguration="Debug"/>
  <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
''')
print(f"Generated {PROJECT.relative_to(ROOT)} ({len(files)} Swift app files)")
