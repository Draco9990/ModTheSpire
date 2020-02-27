package com.evacipated.cardcrawl.modthespire.patcher;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.ByRef;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import javassist.*;

abstract class ParameterPatchInfo extends PatchInfo
{
    // Feature toggles
    protected boolean canSpireReturn = false;
    protected boolean canByRefParams = false;

    protected String src;
    protected String funccall;
    protected String funccallargs;
    protected String postcallsrc;
    protected String postcallsrc2;

    public ParameterPatchInfo(CtBehavior ctMethodToPatch, CtMethod patchMethod)
    {
        super(ctMethodToPatch, patchMethod);
    }

    protected ParamTransformer makeTransformer(ParamInfo src, ParamInfo dest)
    {
        return new ParamTransformer(src, dest);
    }

    protected void alterSrc()
    {
        // NOP
    }

    protected abstract void applyPatch(String src) throws CannotCompileException;

    @Override
    public void doPatch() throws PatchingException
    {
        try {
            boolean hasEarlyReturn = false;
            if (canSpireReturn) {
                CtClass returnType = patchMethod.getReturnType();
                if ((ctMethodToPatch instanceof CtMethod || ctMethodToPatch instanceof CtConstructor)
                    && !returnType.equals(CtPrimitiveType.voidType)
                    && returnType.equals(returnType.getClassPool().get(SpireReturn.class.getName()))
                ) {
                    hasEarlyReturn = true;
                }
            }

            src = "{\n";
            funccall = patchMethod.getDeclaringClass().getName() + "." + patchMethod.getName() + "(%s);\n";
            postcallsrc = "";
            postcallsrc2 = "";

            if (hasEarlyReturn) {
                funccall = SpireReturn.class.getName() + " opt = " + funccall;
            }

            funccallargs = "";
            CtClass[] paramTypes = patchMethod.getParameterTypes();
            int i = 0;
            int j = 0;
            while (j < paramTypes.length) {
                ParamTransformer transformer = makeTransformer(new ParamInfo(ctMethodToPatch, i), new ParamInfo(patchMethod, j));
                transformer.makeSource();
                if (transformer.advanceSrcPosition()) {
                    ++i;
                }
                ++j;
            }

            // Trim ending spaces and ,
            funccallargs = funccallargs.trim();
            if (funccallargs.endsWith(",")) {
                funccallargs = funccallargs.substring(0, funccallargs.length()-1);
            }

            funccall = String.format(funccall, funccallargs);

            alterSrc();

            src += funccall;
            String src2 = src;
            src += postcallsrc;
            src2 += postcallsrc2;

            if (hasEarlyReturn) {
                String earlyReturn = "if (opt.isPresent()) { return";
                if (ctMethodToPatch instanceof CtMethod && !((CtMethod) ctMethodToPatch).getReturnType().equals(CtPrimitiveType.voidType)) {
                    CtClass toPatchReturnType = ((CtMethod) ctMethodToPatch).getReturnType();
                    String toPatchReturnTypeName = toPatchReturnType.getName();
                    if (toPatchReturnType.isPrimitive()) {
                        if (toPatchReturnType.equals(CtPrimitiveType.intType)) {
                            toPatchReturnTypeName = "Integer";
                        } else if (toPatchReturnType.equals(CtPrimitiveType.charType)) {
                            toPatchReturnTypeName = "Character";
                        } else {
                            toPatchReturnTypeName = toPatchReturnTypeName.substring(0, 1).toUpperCase() + toPatchReturnTypeName.substring(1);
                        }
                        earlyReturn += " (";
                    }
                    earlyReturn += " (" + toPatchReturnTypeName + ")opt.get()";
                    if (toPatchReturnType.isPrimitive()) {
                        earlyReturn += ")." + toPatchReturnType.getName() + "Value()";
                    }
                }
                earlyReturn += "; }\n";

                src += earlyReturn;
                src2 += earlyReturn;
            }

            src += "}";
            src2 += "}";

            if (Loader.DEBUG) {
                System.out.println(src);
            }
            try {
                applyPatch(src);
            } catch (CannotCompileException e) {
                try {
                    applyPatch(src2);
                } catch (CannotCompileException e2) {
                    throw e;
                }
            }
        } catch (CannotCompileException | NotFoundException | ClassNotFoundException e) {
            throw new PatchingException(e);
        }
    }

    protected class ParamTransformer
    {
        protected ParamInfo srcInfo;
        protected ParamInfo destInfo;

        protected ParamTransformer(ParamInfo src, ParamInfo dest)
        {
            srcInfo = src;
            destInfo = dest;
        }

        protected boolean advanceSrcPosition()
        {
            return true;
        }

        protected String getParamName() throws PatchingException
        {
            // Formal parameters of original method ("$0", "$1", "$2", etc.)
            if (srcInfo.getPosition() >= 0) {
                return "$" + srcInfo.getPosition();
            }

            if (destInfo.isPrivateCapture()) {
                return destInfo.getName();
            }

            throw new PatchingException("Illegal patch parameter: Cannot determine name");
        }

        protected String boxing(String paramName) throws NotFoundException
        {
            if (!destInfo.getType().equals(srcInfo.getType())) {
                CtClass ctComponentType = destInfo.getType().getComponentType();
                if (srcInfo.getType() != null && srcInfo.getType().isPrimitive() && !ctComponentType.isPrimitive()) {
                    return "new " + ctComponentType.getName() + "(" + paramName + ")";
                }
            }

            return paramName;
        }

        protected void makeSource()
            throws ClassNotFoundException, PatchingException, NotFoundException
        {
            Object[] paramAnnotations = destInfo.getAnnotations();
            if (canByRefParams && paramByRef(paramAnnotations)) {
                if (!destInfo.getType().isArray()) {
                    throw new ByRefParameterNotArrayException(destInfo.getPosition() - 1);
                }
                String tmp = destInfo.getTypename();
                String paramTypeName = tmp.substring(0, tmp.indexOf('[')+1);
                paramTypeName = paramTypeName + "1" + tmp.substring(tmp.indexOf('[')+1);
                // This does
                //   T[][] __var = new T[1][];
                //   __var[0] = var;
                // instead of
                //   T[][] __var = new T[][]{var};
                // to avoid a limitation in the javassist compiler being unable to compile
                // multi-dimensional array initializers
                src += tmp + " __param" + destInfo.getPosition() + " = new " + paramTypeName + ";\n";
                src += "__param" + destInfo.getPosition() + "[0] = " + boxing(getParamName()) + ";\n";
                funccallargs += "__param" + destInfo.getPosition();

                postcallsrc  += getParamName() + " = ";
                postcallsrc2 += getParamName() + " = ";

                String typename = srcInfo.getTypename();
                for (Object o : destInfo.getAnnotations()) {
                    if (o instanceof ByRef && !((ByRef) o).type().isEmpty()) {
                        typename = ((ByRef) o).type();
                    }
                }
                if (!typename.isEmpty()) {
                    postcallsrc  += "(" + typename + ")";
                    postcallsrc2 += "(com.megacrit.cardcrawl." + typename + ")";
                }
                postcallsrc  += "__param" + destInfo.getPosition() + "[0]";
                postcallsrc2 += "__param" + destInfo.getPosition() + "[0]";
                // Unboxing wrapper types
                if (srcInfo.getType() != null && destInfo.getType() != null) {
                    CtClass ctComponentType = destInfo.getType().getComponentType();
                    if (srcInfo.getType().isPrimitive() && ctComponentType != null && !ctComponentType.isPrimitive()) {
                        CtPrimitiveType ctPrimitive = (CtPrimitiveType) srcInfo.getType();
                        postcallsrc += "." + ctPrimitive.getGetMethodName() + "()";
                        postcallsrc2 += "." + ctPrimitive.getGetMethodName() + "()";
                    }
                }
                postcallsrc  += ";\n";
                postcallsrc2 += ";\n";
            } else {
                funccallargs += getParamName();
            }

            funccallargs += ", ";
        }
    }
}
