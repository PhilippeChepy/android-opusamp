package net.opusapp.player.ui.views;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.PixelFormat;
import android.preference.CheckBoxPreference;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import net.opusapp.player.R;
import net.opusapp.player.ui.preference.ColorPickerPreference;

public class ColorSchemeDialog extends AlertDialog {

    private static ColorSchemePreset colorPresets[] = new ColorSchemePreset[] {
        new ColorSchemePreset(0xffe51c23, 0xffb0120a, 0xffffffff, false, R.id.color_scheme_dialog_preset1), // Red
        new ColorSchemePreset(0xffe91e63, 0xff880e4f, 0xffffffff, false, R.id.color_scheme_dialog_preset2), // Pink
        new ColorSchemePreset(0xff9c27b0, 0xff4a148c, 0xffffffff, false, R.id.color_scheme_dialog_preset3), // Purple
        new ColorSchemePreset(0xff673ab7, 0xff311b92, 0xffffffff, false, R.id.color_scheme_dialog_preset4), // Deep Purple
        new ColorSchemePreset(0xff3f51b5, 0xff1a237e, 0xffffffff, false, R.id.color_scheme_dialog_preset5), // Indigo
        new ColorSchemePreset(0xff5677fc, 0xff2a36b1, 0xffffffff, false, R.id.color_scheme_dialog_preset6), // Blue
        new ColorSchemePreset(0xff03a9f4, 0xff01579b, 0xffffffff, false, R.id.color_scheme_dialog_preset7), // Light Blue
        new ColorSchemePreset(0xff00bcd4, 0xff006064, 0xffffffff, false, R.id.color_scheme_dialog_preset8), // Cyan
        new ColorSchemePreset(0xff009688, 0xff004d40, 0xffffffff, false, R.id.color_scheme_dialog_preset9), // Teal
        new ColorSchemePreset(0xff259b24, 0xff0d5302, 0xffffffff, false, R.id.color_scheme_dialog_preset10), // Green
        new ColorSchemePreset(0xff8bc34a, 0xff33691e, 0xffffffff, false, R.id.color_scheme_dialog_preset11), // Light Green
        new ColorSchemePreset(0xffcddc39, 0xff827717, 0xff000000, true, R.id.color_scheme_dialog_preset12),  // Lime
        new ColorSchemePreset(0xffffeb3b, 0xfff57f17, 0xff000000, true, R.id.color_scheme_dialog_preset13),  // Yellow
        new ColorSchemePreset(0xffffc107, 0xffff6f00, 0xff000000, true, R.id.color_scheme_dialog_preset14),  // Amber
        new ColorSchemePreset(0xffff9800, 0xffe65100, 0xff000000, true, R.id.color_scheme_dialog_preset15),  // Orange
        new ColorSchemePreset(0xffff5722, 0xffbf360c, 0xffffffff, false, R.id.color_scheme_dialog_preset16), // Deep Orange
        new ColorSchemePreset(0xff795548, 0xff3e2723, 0xffffffff, false, R.id.color_scheme_dialog_preset17), // Brown
        new ColorSchemePreset(0xff9e9e9e, 0xff212121, 0xff000000, true, R.id.color_scheme_dialog_preset18),  // Grey
        new ColorSchemePreset(0xff607d8b, 0xff263238, 0xffffffff, false, R.id.color_scheme_dialog_preset19)  // Blue Grey
    };

    private ColorPickerPreference primaryColorPreference;

    private ColorPickerPreference accentColorPreference;

    private ColorPickerPreference foregroundColorPreference;

    private CheckBoxPreference useDarkIconsreference;

    /**
     * Constructor of <code>ColorSchemeDialog</code>
     *
     * @param context The {@link Context} to use.
     */
    public ColorSchemeDialog(final Context context) {
        super(context);
        getWindow().setFormat(PixelFormat.RGBA_8888);


        final LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View rootView = inflater.inflate(R.layout.dialog_color_scheme, null);

//  TODO:        setTitle(R.string.color_picker_title);
        setView(rootView);
        for (final ColorSchemePreset preset : colorPresets) {
            Button button = (Button) rootView.findViewById(preset.selector);
            button.setBackgroundColor(preset.background);
            button.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(final View v) {
                    int viewId = v.getId();

                    for (final ColorSchemePreset preset : colorPresets) {
                        if (viewId == preset.selector) {
                            if (primaryColorPreference != null) {
                                primaryColorPreference.onColorChanged(preset.background);
                            }
                            if (accentColorPreference != null) {
                                accentColorPreference.onColorChanged(preset.accent);
                            }
                            if (foregroundColorPreference != null) {
                                foregroundColorPreference.onColorChanged(preset.foreground);
                            }
                            if (useDarkIconsreference != null) {
                                useDarkIconsreference.setChecked(preset.dark);
                            }
                            dismiss();
                            break;
                        }
                    }
                }
            });
        }
    }

    public void setPreferences(ColorPickerPreference primaryColorPreference,
                               ColorPickerPreference accentColorPreference,
                               ColorPickerPreference foregroundColorPreference,
                               CheckBoxPreference useDarkIconsPreference) {
        this.primaryColorPreference = primaryColorPreference;
        this.accentColorPreference = accentColorPreference;
        this.foregroundColorPreference = foregroundColorPreference;
        this.useDarkIconsreference = useDarkIconsPreference;
    }

    /**
     * Sets up the preset buttons
     */

    public static class ColorSchemePreset {

        int foreground;

        int accent;

        int background;

        boolean dark;

        int selector;

        public ColorSchemePreset(int background, int accent, int foreground, boolean dark, int selector) {
            this.foreground = foreground;
            this.accent = accent;
            this.background = background;
            this.dark = dark;
            this.selector = selector;
        }
    }

}
