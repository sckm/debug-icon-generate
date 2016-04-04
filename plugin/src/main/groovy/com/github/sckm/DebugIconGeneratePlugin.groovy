package com.github.sckm

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariantOutput
import groovy.io.FileType
import org.gradle.api.Plugin
import org.gradle.api.Project

import javax.imageio.ImageIO
import java.awt.*
import java.awt.image.BufferedImage
import java.util.List

class DebugIconGeneratePlugin implements Plugin<Project> {
    def static int MDPI_ICON_PIXEL_SIZE = 48;

    @Override
    void apply(Project project) {
        project.extensions.create("debugIconGenerate", PluginExtension)

        project.android.applicationVariants.all { ApplicationVariant variant ->
            if (!variant.buildType.debuggable) {
                return
            }

            variant.outputs.each { BaseVariantOutput output ->
                output.processResources.outputs.upToDateWhen { false }
                output.processResources.doFirst {
                    findIconFiles(output).each { File icon ->
                        def buildName = variant.flavorName + " " + variant.buildType.name

                        def versionName = (project.debugIconGenerate.versionText.isEmpty()
                                ? variant.versionName
                                : project.debugIconGenerate.versionText)

                        def fontSize = project.debugIconGenerate.fontSize
                        def fontName = project.debugIconGenerate.fontName

                        // TODO args should be optional
                        drawTextToImageFile(icon, [buildName, versionName] as String[], fontSize, fontName)
                    }
                }
            }
        }
    }

    static File[] findIconFiles(BaseVariantOutput output) {
        File manifest = output.processManifest.manifestOutputFile
        File resDir = output.processResources.resDir
        if (!manifest.exists() || !resDir.exists()) {
            return []
        }
        findFiles(resDir, getIconFileName(manifest))
    }

    static File[] findFiles(File resourceDir, String fileName) {
        List<File> files = []
        resourceDir.eachDir {
            it.eachFileMatch(FileType.FILES, ~/${fileName}.*/) { file ->
                files.add(file)
            }
        }
        files
    }

    static String getIconFileName(File manifestFile) {
        def manifest = new XmlSlurper().parse(manifestFile)
        def iconName = manifest.application.@'android:icon'
        (iconName as String).split("/")[1]
    }

    // TODO add line margin
    // TODO change font size depending on image size
    static void drawTextToImageFile(File imageFile, String[] texts, int fontSize, String fontName) {
        BufferedImage bufImage = ImageIO.read(imageFile)
        int width = bufImage.width
        int height = bufImage.height

        int scaledFontSize = width * fontSize / MDPI_ICON_PIXEL_SIZE;

        Graphics2D g = bufImage.createGraphics();
        g.setFont(new Font(fontName, Font.PLAIN, scaledFontSize))
        int lineHeight = g.getFontMetrics().getHeight()

        // TODO changeable color
        // draw background
        g.setColor(new Color(128, 128, 128, 128))
        int textsHeight = texts.length * lineHeight
        g.fillRect(0, height - textsHeight, width, textsHeight)

        // TODO changeable color
        // draw texts
        g.setColor(new Color(255, 255, 255, 255))
        int curHeight = bufImage.height
        texts.reverse().each { text ->
            int textWidth = g.getFontMetrics().stringWidth(text)
            int startX = [0, (bufImage.width - textWidth) / 2].max();
            g.drawString(text, startX, curHeight)
            curHeight -= lineHeight
        }

        // output
        ImageIO.write(bufImage, "png", imageFile)
    }
}
