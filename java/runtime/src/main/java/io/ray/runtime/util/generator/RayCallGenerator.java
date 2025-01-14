package io.ray.runtime.util.generator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;

/**
 * A util class that generates `RayCall.java` and `ActorCall.java`, which provide type-safe
 * interfaces for `Ray.call`, `Ray.createActor` and `actor.call`.
 */
public class RayCallGenerator extends BaseGenerator {

  /** Returns Whole file content of `RayCall.java`. */
  private String generateRayCallDotJava() {
    sb = new StringBuilder();

    newLine("// Generated by `RayCallGenerator.java`. DO NOT EDIT.");
    newLine("");
    newLine("package io.ray.api;");
    newLine("");
    newLine("import io.ray.api.call.ActorCreator;");
    newLine("import io.ray.api.call.CppActorCreator;");
    newLine("import io.ray.api.call.PyActorCreator;");
    newLine("import io.ray.api.call.PyTaskCaller;");
    newLine("import io.ray.api.call.TaskCaller;");
    newLine("import io.ray.api.call.VoidTaskCaller;");
    newLine("import io.ray.api.function.CppActorClass;");
    newLine("import io.ray.api.function.PyActorClass;");
    newLine("import io.ray.api.function.PyFunction;");
    for (int i = 0; i <= MAX_PARAMETERS; i++) {
      newLine("import io.ray.api.function.RayFunc" + i + ";");
    }
    for (int i = 0; i <= MAX_PARAMETERS; i++) {
      newLine("import io.ray.api.function.RayFuncVoid" + i + ";");
    }
    newLine("");

    newLine("/**");
    newLine(" * This class provides type-safe interfaces for `Ray.call` and `Ray.createActor`.");
    newLine(" **/");
    newLine("class RayCall {");
    newLine(1, "// =======================================");
    newLine(1, "// Methods for remote function invocation.");
    newLine(1, "// =======================================");
    for (int i = 0; i <= MAX_PARAMETERS; i++) {
      buildCalls(i, false, false, true);
      buildCalls(i, false, false, false);
    }

    newLine(1, "// ===========================");
    newLine(1, "// Methods for actor creation.");
    newLine(1, "// ===========================");
    for (int i = 0; i <= MAX_PARAMETERS; i++) {
      buildCalls(i, false, true, true);
    }

    newLine(1, "// ===========================");
    newLine(1, "// Cross-language methods.");
    newLine(1, "// ===========================");
    for (int i = 0; i <= MAX_PARAMETERS; i++) {
      buildPyCalls(i, false, false);
    }
    for (int i = 0; i <= MAX_PARAMETERS; i++) {
      buildPyCalls(i, false, true);
    }
    for (int i = 0; i <= MAX_PARAMETERS; i++) {
      buildCppCalls(i, false, true);
    }
    newLine("}");
    return sb.toString();
  }

  /** Returns Whole file content of `ActorCall.java`. */
  private String generateActorCallDotJava() {
    sb = new StringBuilder();

    newLine("// Generated by `RayCallGenerator.java`. DO NOT EDIT.");
    newLine("");
    newLine("package io.ray.api;");
    newLine("");
    newLine("import io.ray.api.call.ActorTaskCaller;");
    newLine("import io.ray.api.call.VoidActorTaskCaller;");
    for (int i = 1; i <= MAX_PARAMETERS; i++) {
      newLine("import io.ray.api.function.RayFunc" + i + ";");
    }
    for (int i = 1; i <= MAX_PARAMETERS; i++) {
      newLine("import io.ray.api.function.RayFuncVoid" + i + ";");
    }
    newLine("");
    newLine("/**");
    newLine(" * This class provides type-safe interfaces for remote actor calls.");
    newLine(" **/");
    newLine("interface ActorCall<A> {");
    newLine("");
    for (int i = 0; i <= MAX_PARAMETERS - 1; i++) {
      buildCalls(i, true, false, true);
      buildCalls(i, true, false, false);
    }
    newLine("}");
    return sb.toString();
  }

  /** Returns Whole file content of `PyActorCall.java`. */
  private String generatePyActorCallDotJava() {
    sb = new StringBuilder();

    newLine("// Generated by `RayCallGenerator.java`. DO NOT EDIT.");
    newLine("");
    newLine("package io.ray.api;");
    newLine("");
    newLine("import io.ray.api.call.PyActorTaskCaller;");
    newLine("import io.ray.api.function.PyActorMethod;");
    newLine("");
    newLine("/**");
    newLine(" * This class provides type-safe interfaces for remote actor calls.");
    newLine(" **/");
    newLine("interface PyActorCall {");
    newLine("");
    for (int i = 0; i <= MAX_PARAMETERS - 1; i++) {
      buildPyCalls(i, true, false);
    }
    newLine("}");
    return sb.toString();
  }

  /** Returns Whole file content of `CppActorCall.java`. */
  private String generateCppActorCallDotJava() {
    sb = new StringBuilder();

    newLine("// Generated by `RayCallGenerator.java`. DO NOT EDIT.");
    newLine("");
    newLine("package io.ray.api;");
    newLine("");
    newLine("import io.ray.api.call.CppActorTaskCaller;");
    newLine("import io.ray.api.function.CppActorMethod;");
    newLine("");
    newLine("/**");
    newLine(" * This class provides type-safe interfaces for remote actor calls.");
    newLine(" **/");
    newLine("interface CppActorCall {");
    newLine("");
    for (int i = 0; i <= MAX_PARAMETERS - 1; i++) {
      buildCppCalls(i, true, false);
    }
    newLine("}");
    return sb.toString();
  }

  /**
   * Build `Ray.call`, `Ray.createActor` and `actor.call` methods with the given number of
   * parameters.
   *
   * @param numParameters the number of parameters
   * @param forActor Build `actor.call` when true, otherwise build `Ray.call`.
   * @param hasReturn if true, Build api for functions with return.
   * @param forActorCreation Build `Ray.createActor` when true, otherwise build `Ray.call`.
   */
  private void buildCalls(
      int numParameters, boolean forActor, boolean forActorCreation, boolean hasReturn) {
    // Template of the generated function:
    // [modifiers] [genericTypes] [returnType] [callFunc]([argsDeclaration]) {
    //   Objects[] args = new Object[]{[args]};
    //   return new [Caller](func, args);
    // }

    String modifiers = forActor ? "default" : "public static";

    // 1) Construct the `genericTypes` part, e.g. `<T0, T1, T2, R>`.
    String genericTypes = "";
    for (int i = 0; i < numParameters; i++) {
      genericTypes += "T" + i + ", ";
    }
    // Return generic type.
    if (forActorCreation) {
      genericTypes += "A, ";
    } else {
      if (hasReturn) {
        genericTypes += "R, ";
      }
    }
    if (!genericTypes.isEmpty()) {
      // Trim trailing ", ";
      genericTypes = genericTypes.substring(0, genericTypes.length() - 2);
      genericTypes = "<" + genericTypes + ">";
    }

    // 2) Construct the `returnType` part.
    String returnType;
    if (forActorCreation) {
      returnType = "ActorCreator<A>";
    } else {
      if (forActor) {
        returnType = hasReturn ? "ActorTaskCaller<R>" : "VoidActorTaskCaller";
      } else {
        returnType = hasReturn ? "TaskCaller<R>" : "VoidTaskCaller";
      }
    }

    // 3) Construct the `argsDeclaration` part.
    String rayFuncGenericTypes = genericTypes;
    if (forActor) {
      if (rayFuncGenericTypes.isEmpty()) {
        rayFuncGenericTypes = "<A>";
      } else {
        rayFuncGenericTypes = rayFuncGenericTypes.replace("<", "<A, ");
      }
    }
    String argsDeclarationPrefix =
        String.format(
            "RayFunc%s%d%s f, ",
            hasReturn ? "" : "Void",
            !forActor ? numParameters : numParameters + 1,
            rayFuncGenericTypes);

    String callFunc = forActorCreation ? "actor" : "task";
    String caller;
    if (forActorCreation) {
      caller = "ActorCreator<>";
    } else {
      if (forActor) {
        caller = hasReturn ? "ActorTaskCaller<>" : "VoidActorTaskCaller";
      } else {
        caller = hasReturn ? "TaskCaller<>" : "VoidTaskCaller";
      }
    }

    // Enumerate all combinations of the parameters.
    for (String param : generateParameters(numParameters)) {
      String argsDeclaration = argsDeclarationPrefix + param;
      // Trim trailing ", ";
      argsDeclaration = argsDeclaration.substring(0, argsDeclaration.length() - 2);
      // Print the first line (method signature).
      newLine(
          1,
          String.format(
              "%s%s %s %s(%s) {",
              modifiers,
              genericTypes.isEmpty() ? "" : " " + genericTypes,
              returnType,
              callFunc,
              argsDeclaration));

      // 4) Construct the `args` part.
      String args = "";
      for (int i = 0; i < numParameters; i++) {
        args += "t" + i + ", ";
      }
      // Trim trailing ", ";
      if (!args.isEmpty()) {
        args = args.substring(0, args.length() - 2);
      }
      // Print the second line (local args declaration).
      newLine(2, String.format("Object[] args = new Object[]{%s};", args));

      // 5) Construct the third line.
      String ctrArgs = "";
      if (forActor) {
        ctrArgs += "(ActorHandle) this, ";
      }
      ctrArgs += "f, args, ";
      ctrArgs = ctrArgs.substring(0, ctrArgs.length() - 2);
      newLine(2, String.format("return new %s(%s);", caller, ctrArgs));
      newLine(1, "}");
      newLine("");
    }
  }

  /**
   * Build `Ray.call`, `Ray.createActor` and `actor.call` methods with the given number of
   * parameters.
   *
   * @param numParameters the number of parameters
   * @param forActor Build `actor.call` when true, otherwise build `Ray.call`.
   * @param forActorCreation Build `Ray.createActor` when true, otherwise build `Ray.call`.
   */
  private void buildPyCalls(int numParameters, boolean forActor, boolean forActorCreation) {
    String modifiers = forActor ? "default" : "public static";

    String argList = "";
    String paramList = "";
    for (int i = 0; i < numParameters; i++) {
      paramList += "Object obj" + i + ", ";
      argList += "obj" + i + ", ";
    }
    if (argList.endsWith(", ")) {
      argList = argList.substring(0, argList.length() - 2);
    }
    if (paramList.endsWith(", ")) {
      paramList = paramList.substring(0, paramList.length() - 2);
    }

    String paramPrefix = "";
    String funcArgs = "";
    if (forActorCreation) {
      paramPrefix += "PyActorClass pyActorClass";
      funcArgs += "pyActorClass";
    } else if (forActor) {
      paramPrefix += "PyActorMethod<R> pyActorMethod";
      funcArgs += "pyActorMethod";
    } else {
      paramPrefix += "PyFunction<R> pyFunction";
      funcArgs += "pyFunction";
    }
    if (numParameters > 0) {
      paramPrefix += ", ";
    }

    String genericType = forActorCreation ? "" : " <R>";
    String returnType =
        forActorCreation ? "PyActorCreator" : forActor ? "PyActorTaskCaller<R>" : "PyTaskCaller<R>";

    String funcName = forActorCreation ? "actor" : "task";
    String caller =
        forActorCreation ? "PyActorCreator" : forActor ? "PyActorTaskCaller<>" : "PyTaskCaller<>";
    funcArgs += ", args";
    // Method signature.
    newLine(
        1,
        String.format(
            "%s%s %s %s(%s) {",
            modifiers, genericType, returnType, funcName, paramPrefix + paramList));
    // Method body.
    newLine(2, String.format("Object[] args = new Object[]{%s};", argList));
    if (forActor) {
      newLine(2, String.format("return new %s((PyActorHandle)this, %s);", caller, funcArgs));
    } else {
      newLine(2, String.format("return new %s(%s);", caller, funcArgs));
    }
    newLine(1, "}");
    newLine("");
  }

  private void buildCppCalls(int numParameters, boolean forActor, boolean forActorCreation) {
    String modifiers = forActor ? "default" : "public static";

    String argList = "";
    String paramList = "";
    for (int i = 0; i < numParameters; i++) {
      paramList += "Object obj" + i + ", ";
      argList += "obj" + i + ", ";
    }
    if (argList.endsWith(", ")) {
      argList = argList.substring(0, argList.length() - 2);
    }
    if (paramList.endsWith(", ")) {
      paramList = paramList.substring(0, paramList.length() - 2);
    }

    String paramPrefix = "";
    String funcArgs = "";
    if (forActorCreation) {
      paramPrefix += "CppActorClass cppActorClass";
      funcArgs += "cppActorClass";
    } else if (forActor) {
      paramPrefix += "CppActorMethod<R> cppActorMethod";
      funcArgs += "cppActorMethod";
    } else {
      paramPrefix += "CppFunction<R> cppFunction";
      funcArgs += "cppFunction";
    }
    if (numParameters > 0) {
      paramPrefix += ", ";
    }

    String genericType = forActorCreation ? "" : " <R>";
    String returnType =
        forActorCreation
            ? "CppActorCreator"
            : forActor ? "CppActorTaskCaller<R>" : "CppTaskCaller<R>";

    String funcName = forActorCreation ? "actor" : "task";
    String caller =
        forActorCreation
            ? "CppActorCreator"
            : forActor ? "CppActorTaskCaller<>" : "CppTaskCaller<>";
    funcArgs += ", args";
    // Method signature.
    newLine(
        1,
        String.format(
            "%s%s %s %s(%s) {",
            modifiers, genericType, returnType, funcName, paramPrefix + paramList));
    // Method body.
    newLine(2, String.format("Object[] args = new Object[]{%s};", argList));
    if (forActor) {
      newLine(2, String.format("return new %s((CppActorHandle)this, %s);", caller, funcArgs));
    } else {
      newLine(2, String.format("return new %s(%s);", caller, funcArgs));
    }
    newLine(1, "}");
    newLine("");
  }

  private List<String> generateParameters(int numParams) {
    List<String> res = new ArrayList<>();
    dfs(0, numParams, "", res);
    return res;
  }

  private void dfs(int pos, int numParams, String cur, List<String> res) {
    if (pos >= numParams) {
      res.add(cur);
      return;
    }
    String nextParameter = String.format("T%d t%d, ", pos, pos);
    dfs(pos + 1, numParams, cur + nextParameter, res);
    nextParameter = String.format("ObjectRef<T%d> t%d, ", pos, pos);
    dfs(pos + 1, numParams, cur + nextParameter, res);
  }

  public static void main(String[] args) throws IOException {
    String path = System.getProperty("user.dir") + "/api/src/main/java/io/ray/api/RayCall.java";
    FileUtils.write(
        new File(path), new RayCallGenerator().generateRayCallDotJava(), Charset.defaultCharset());
    path = System.getProperty("user.dir") + "/api/src/main/java/io/ray/api/ActorCall.java";
    FileUtils.write(
        new File(path),
        new RayCallGenerator().generateActorCallDotJava(),
        Charset.defaultCharset());
    path = System.getProperty("user.dir") + "/api/src/main/java/io/ray/api/PyActorCall.java";
    FileUtils.write(
        new File(path),
        new RayCallGenerator().generatePyActorCallDotJava(),
        Charset.defaultCharset());
    path = System.getProperty("user.dir") + "/api/src/main/java/io/ray/api/CppActorCall.java";
    FileUtils.write(
        new File(path),
        new RayCallGenerator().generateCppActorCallDotJava(),
        Charset.defaultCharset());
  }
}
