package mcx.script

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

@Suppress("unused")
object Agent {
  @JvmStatic
  fun agentmain(
    args: String?,
    instrumentation: Instrumentation,
  ) {
    instrumentation.addTransformer(object : ClassFileTransformer {
      override fun transform(
        loader: ClassLoader?,
        className: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain,
        classfileBuffer: ByteArray,
      ): ByteArray? {
        fun transform(block: (ClassVisitor) -> ClassVisitor): ByteArray {
          val reader = ClassReader(classfileBuffer)
          val writer = ClassWriter(reader, 0)
          val visitor = block(writer)
          reader.accept(visitor, ClassReader.EXPAND_FRAMES)
          return writer.toByteArray()
        }

        return when (className) {
          "net/minecraft/server/MinecraftServer"   -> transform {
            object : ClassVisitor(ASM9, it) {
              override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
              ): MethodVisitor {
                val parent = super.visitMethod(access, name, descriptor, signature, exceptions)
                return if (name == "getServerModName" && descriptor == "()Ljava/lang/String;") {
                  object : MethodVisitor(ASM9, parent) {
                    override fun visitLdcInsn(value: Any?) {
                      super.visitLdcInsn("mcx")
                    }
                  }
                } else {
                  parent
                }
              }
            }
          }
          "com/mojang/brigadier/CommandDispatcher" -> transform {
            object : ClassVisitor(ASM9, it) {
              override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
              ): MethodVisitor {
                val parent = super.visitMethod(access, name, descriptor, signature, exceptions)
                return if (name == "<init>" && descriptor == "(Lcom/mojang/brigadier/tree/RootCommandNode;)V") {
                  object : MethodVisitor(ASM9, parent) {
                    override fun visitInsn(opcode: Int) {
                      if (opcode == RETURN) {
                        visitVarInsn(ALOAD, 0)
                        visitMethodInsn(INVOKESTATIC, "mcx/script/Commands", "register", "(Lcom/mojang/brigadier/CommandDispatcher;)V", false)
                      }
                      super.visitInsn(opcode)
                    }
                  }
                } else {
                  parent
                }
              }
            }
          }
          else                                     -> null
        }
      }
    })
  }
}
