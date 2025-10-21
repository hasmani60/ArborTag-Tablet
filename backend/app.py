"""
ArborTag Backend API - Complete with All Your Python Models
Includes: python_distribution.py, python_heatmap_png1.py, python_diversity_png1.py
"""

from flask import Flask, request, send_file, jsonify
from flask_cors import CORS
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
import numpy as np
import os
import tempfile
import folium
from folium.plugins import HeatMap
from branca.colormap import LinearColormap
import contextily as cx

app = Flask(__name__)
CORS(app)

# Temporary directory for outputs
TEMP_DIR = tempfile.mkdtemp()

# ============================================================================
# MODEL 1: From python_distribution.py
# ============================================================================

def adjust_color_brightness_saturation(rgba_color, brightness_factor=0.8, saturation_factor=0.6):
    """EXACT function from python_distribution.py"""
    # Extract RGB components
    rgb_color = rgba_color[:3]

    # Convert RGB to HSV
    hsv_color = mcolors.rgb_to_hsv(rgb_color)

    # Adjust brightness (V channel)
    hsv_color[2] = np.clip(hsv_color[2] * brightness_factor, 0, 1)

    # Adjust saturation (S channel)
    hsv_color[1] = np.clip(hsv_color[1] * saturation_factor, 0, 1)

    # Convert back to RGB and recombine with alpha
    adjusted_rgb = mcolors.hsv_to_rgb(hsv_color)
    return (adjusted_rgb[0], adjusted_rgb[1], adjusted_rgb[2], rgba_color[3])

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "message": "ArborTag Analysis API with All Models",
        "version": "3.0.0",
        "models": ["distribution", "heatmap", "diversity", "height", "width", "summary"]
    })

@app.route('/analyze/distribution', methods=['POST'])
def analyze_distribution():
    """EXACT implementation from python_distribution.py"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        # Count the occurrences of each scientific name
        name_counts = data['scientific_name'].value_counts()

        # Combine tab20, tab20b, and tab20c
        tab20_combined = plt.cm.get_cmap('tab20', 20)
        tab20b_combined = plt.cm.get_cmap('tab20b', 20)
        tab20c_combined = plt.cm.get_cmap('tab20c', 20)

        # Creating a new combined color list
        combined_colors = list(tab20_combined.colors) + list(tab20b_combined.colors) + list(tab20c_combined.colors)

        # Adjust color brightness and saturation
        combined_colors = [adjust_color_brightness_saturation(color) for color in combined_colors]

        # Truncate or repeat the color list to match the number of categories
        num_categories = len(name_counts)
        if num_categories > len(combined_colors):
            combined_colors = combined_colors * (num_categories // len(combined_colors) + 1)
        combined_colors = combined_colors[:num_categories]

        # Create the pie chart without internal percentage labels
        plt.figure(figsize=(30, 22))
        patches, texts = plt.pie(name_counts, colors=combined_colors, startangle=140)
        plt.axis('equal')  # Equal aspect ratio ensures the pie chart is circular.

        title_font_size = 34  # Adjust this value for the title font size
        plt.title('Distribution of Trees by Scientific Name', fontsize=title_font_size)

        # Calculate percentage and update legend labels
        percentages = [f'{p / sum(name_counts) * 100:.1f}%' for p in name_counts]
        legend_labels = [f'{name} - {percent}' for name, percent in zip(name_counts.index, percentages)]

        legend_font_size = 28  # Adjust this value for the legend items font size
        legend_title_font_size = 30  # Adjust this value for the legend title font size
        legend = plt.legend(patches, legend_labels, title="Scientific Names", loc="center left",
                           bbox_to_anchor=(1, 0.5), fontsize=legend_font_size)
        plt.setp(legend.get_title(), fontsize=legend_title_font_size)  # Set the font size of the legend title

        # Adjust the subplot parameters to give some padding
        plt.subplots_adjust(right=0.6)

        # Save the figure as a PNG file
        output_path = os.path.join(TEMP_DIR, 'Distribution.png')
        plt.savefig(output_path)
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ============================================================================
# MODEL 2: From python_heatmap_png1.py
# ============================================================================

def add_matching_gradient_legend_to_map(folium_map, title, vmin, vmax):
    """EXACT function from python_heatmap_png1.py"""
    colors = ['#00FF00', '#FFFF00', '#FFA500', '#FF0000']  # green to red
    colormap = LinearColormap(colors, vmin=vmin, vmax=vmax)
    colormap.caption = title
    folium_map.add_child(colormap)

@app.route('/analyze/heatmap', methods=['POST'])
def analyze_heatmap():
    """Carbon sequestration heatmap - Interactive HTML for mobile"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        # Prepare data for the heatmap
        heat_data = [[row['lat'], row['long'], row['carbon_seq']] for index, row in data.iterrows()]

        # Create the map centered around the average coordinates
        map_center = [data['lat'].mean(), data['long'].mean()]
        heatmap_map = folium.Map(location=map_center, zoom_start=16,
                                 tiles='OpenStreetMap',
                                 control_scale=True)

        # Add HeatMap to the map
        HeatMap(heat_data,
                min_opacity=0.3,
                max_opacity=0.8,
                radius=25,
                blur=20,
                gradient={0.0: '#00FF00', 0.4: '#FFFF00', 0.7: '#FFA500', 1.0: '#FF0000'}).add_to(heatmap_map)

        # Add the matching gradient legend to the map
        add_matching_gradient_legend_to_map(heatmap_map, "Carbon Sequestration Level (kg CO₂/year)",
                                           data['carbon_seq'].min(),
                                           data['carbon_seq'].max())

        # Save the map as HTML
        output_path = os.path.join(TEMP_DIR, 'carbon_seq_heatmap.html')
        heatmap_map.save(output_path)

        # Return HTML content
        with open(output_path, 'r') as f:
            html_content = f.read()

        return html_content, 200, {'Content-Type': 'text/html'}

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/heatmap_static', methods=['POST'])
def analyze_heatmap_static():
    """Carbon sequestration heatmap - PNG with visible map background like Folium"""
    try:
        from scipy.ndimage import gaussian_filter

        file = request.files['file']
        data = pd.read_csv(file)

        # Create figure
        fig, ax = plt.subplots(figsize=(12, 10))

        # Calculate bounds with padding
        lat_margin = (data['lat'].max() - data['lat'].min()) * 0.15 or 0.001
        long_margin = (data['long'].max() - data['long'].min()) * 0.15 or 0.001

        xlim = [data['long'].min() - long_margin, data['long'].max() + long_margin]
        ylim = [data['lat'].min() - lat_margin, data['lat'].max() + lat_margin]

        ax.set_xlim(xlim)
        ax.set_ylim(ylim)

        # ADD MAP BACKGROUND FIRST
        try:
            cx.add_basemap(ax, crs='EPSG:4326',
                          source=cx.providers.OpenStreetMap.Mapnik,
                          attribution=False,
                          zoom='auto',
                          alpha=1.0)
        except Exception as e:
            print(f"Warning: Could not add basemap: {e}")
            ax.set_facecolor('#E8E8E8')

        # Create HIGHER RESOLUTION heatmap grid
        grid_size = 500  # Increased resolution
        x_grid = np.linspace(xlim[0], xlim[1], grid_size)
        y_grid = np.linspace(ylim[0], ylim[1], grid_size)

        # Initialize heatmap
        heatmap_data = np.zeros((grid_size, grid_size))

        # Calculate grid spacing
        x_spacing = (xlim[1] - xlim[0]) / grid_size
        y_spacing = (ylim[1] - ylim[0]) / grid_size

        # Add weighted points with LARGER influence radius
        for _, row in data.iterrows():
            xi = int((row['long'] - xlim[0]) / x_spacing)
            yi = int((row['lat'] - ylim[0]) / y_spacing)

            if 0 <= xi < grid_size and 0 <= yi < grid_size:
                # Create a more prominent heat blob by adding to surrounding pixels
                radius = 20  # pixels
                for dx in range(-radius, radius + 1):
                    for dy in range(-radius, radius + 1):
                        nx, ny = xi + dx, yi + dy
                        if 0 <= nx < grid_size and 0 <= ny < grid_size:
                            # Calculate distance from center
                            dist = np.sqrt(dx*dx + dy*dy)
                            if dist <= radius:
                                # Weight falls off with distance
                                weight = (1 - dist/radius) * row['carbon_seq']
                                heatmap_data[ny, nx] += weight

        # Apply Gaussian blur for smooth gradient
        sigma = 8  # Moderate blur for smooth but visible blobs
        heatmap_smooth = gaussian_filter(heatmap_data, sigma=sigma)

        # Normalize to make it more visible
        if heatmap_smooth.max() > 0:
            heatmap_smooth = heatmap_smooth / heatmap_smooth.max()

        # Mask very low values to keep them transparent
        heatmap_smooth = np.ma.masked_where(heatmap_smooth < 0.05, heatmap_smooth)

        # Create VIBRANT color gradient: Green -> Yellow -> Orange -> Red
        colors_list = ['#00FF00', '#7FFF00', '#FFFF00', '#FFD700', '#FFA500', '#FF6347', '#FF0000']
        n_bins = 256
        cmap = mcolors.LinearSegmentedColormap.from_list('heatmap', colors_list, N=n_bins)
        cmap.set_bad(alpha=0)

        # Plot heatmap with HIGHER alpha for visibility
        heatmap_plot = ax.imshow(heatmap_smooth,
                                 extent=[xlim[0], xlim[1], ylim[0], ylim[1]],
                                 origin='lower',
                                 cmap=cmap,
                                 alpha=0.65,  # Increased from 0.5 to 0.65 for more visibility
                                 interpolation='bilinear',
                                 vmin=0,
                                 vmax=1,
                                 zorder=3)

        # Add white dot markers for tree locations
        ax.scatter(data['long'], data['lat'],
                  c='white',
                  s=40,  # Larger dots
                  alpha=0.95,
                  edgecolors='black',
                  linewidth=1.2,
                  zorder=5)

        # Add colorbar
        cbar = plt.colorbar(heatmap_plot, ax=ax, pad=0.02, shrink=0.8, fraction=0.046)
        cbar.set_label('Carbon Sequestration\n(kg CO₂/year)',
                      fontsize=11,
                      fontweight='bold')
        cbar.ax.tick_params(labelsize=9)

        # Labels and title
        ax.set_xlabel('Longitude', fontsize=14, fontweight='bold')
        ax.set_ylabel('Latitude', fontsize=14, fontweight='bold')
        ax.set_title('Carbon Sequestration Heatmap', fontsize=18, fontweight='bold', pad=15)

        # Stats box
        total_carbon = data['carbon_seq'].sum()
        avg_carbon = data['carbon_seq'].mean()
        num_trees = len(data)

        stats_text = f'Trees: {num_trees}\nTotal: {total_carbon:.1f} kg CO₂/yr\nAvg: {avg_carbon:.1f} kg CO₂/yr'
        ax.text(0.02, 0.98,
                stats_text,
                transform=ax.transAxes,
                verticalalignment='top',
                bbox=dict(boxstyle='round', facecolor='white', alpha=0.95, edgecolor='black', linewidth=1.5),
                fontsize=10,
                fontweight='bold',
                zorder=6)

        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'carbon_heatmap_static.png')
        plt.savefig(output_path, dpi=150, facecolor='white', bbox_inches='tight')
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500


# ============================================================================
# MODEL 3: From python_diversity_png1.py
# ============================================================================

def create_colormap(unique_values):
    """EXACT function from python_diversity_png1.py"""
    colors = list(mcolors.TABLEAU_COLORS.values())  # Using Tableau's color scheme
    color_count = len(colors)
    colormap = {value: colors[i % color_count] for i, value in enumerate(unique_values)}
    return colormap

def plot_points_with_legend(map_obj, data, colormap, column_name):
    """FIXED function from python_diversity_png1.py"""
    for _, row in data.iterrows():
        folium.CircleMarker(
            location=[row['lat'], row['long']],
            radius=8,
            color=colormap[row['scientific_name']],  # FIXED: use actual column name
            fill=True,
            fill_color=colormap[row['scientific_name']],  # FIXED: use actual column name
            fill_opacity=0.7,
            weight=2
        ).add_to(map_obj)

    legend_html = '''<div style="position: fixed; bottom: 50px; left: 50px; width: 220px;
                    height: auto; max-height: 400px; overflow-y: auto; background-color: white;
                    border:2px solid grey; z-index:9999; padding: 10px; border-radius: 5px;">
                    <h4 style="margin-top: 0; margin-bottom: 10px; font-size: 16px;">{}</h4>'''.format(column_name)
    for name, color in colormap.items():
        legend_html += '<div style="margin-bottom: 5px;"><i style="background: {}; width: 20px; height: 20px; \
                        display: inline-block; vertical-align: middle; border: 1px solid #000; margin-right: 8px;"></i> \
                        <span style="font-size: 12px;">{}</span></div>'.format(color, name)
    legend_html += '</div>'
    map_obj.get_root().html.add_child(folium.Element(legend_html))

@app.route('/analyze/diversity', methods=['POST'])
def analyze_diversity():
    """Species diversity map - Interactive HTML"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        # Create a color map
        unique_scientific_names = data['scientific_name'].unique()
        scientific_name_colormap = create_colormap(unique_scientific_names)

        # Create the map centered around the average coordinates
        map_center = [data['lat'].mean(), data['long'].mean()]
        map_with_points = folium.Map(location=map_center, zoom_start=16,
                                     tiles='OpenStreetMap',
                                     control_scale=True)

        # Plot points with a legend - FIXED: pass display name only
        plot_points_with_legend(map_with_points, data, scientific_name_colormap, "Species")

        # Save the map as HTML
        output_path = os.path.join(TEMP_DIR, 'diversity.html')
        map_with_points.save(output_path)

        # Return HTML content
        with open(output_path, 'r') as f:
            html_content = f.read()

        return html_content, 200, {'Content-Type': 'text/html'}

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/diversity_static', methods=['POST'])
def analyze_diversity_static():
    """Species diversity map - PNG version with map background"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        # Create figure with better sizing for mobile
        fig, ax = plt.subplots(figsize=(12, 10))

        # Get unique species and assign colors (using Tableau)
        unique_species = sorted(data['scientific_name'].unique())
        tableau_colors = list(mcolors.TABLEAU_COLORS.values())
        color_map = {species: tableau_colors[i % len(tableau_colors)]
                     for i, species in enumerate(unique_species)}

        # Calculate bounds with padding
        lat_margin = (data['lat'].max() - data['lat'].min()) * 0.15 or 0.001
        long_margin = (data['long'].max() - data['long'].min()) * 0.15 or 0.001

        ax.set_xlim(data['long'].min() - long_margin, data['long'].max() + long_margin)
        ax.set_ylim(data['lat'].min() - lat_margin, data['lat'].max() + lat_margin)

        # Add OpenStreetMap basemap FIRST with full opacity
        try:
            cx.add_basemap(ax, crs='EPSG:4326',
                          source=cx.providers.OpenStreetMap.Mapnik,
                          attribution=False,
                          zoom='auto',
                          alpha=1.0)
        except Exception as e:
            print(f"Warning: Could not add basemap: {e}")
            ax.grid(True, alpha=0.3, linestyle='--', linewidth=0.5)

        # Plot each species with its assigned color
        for species in unique_species:
            species_data = data[data['scientific_name'] == species]
            ax.scatter(species_data['long'], species_data['lat'],
                      c=color_map[species],
                      label=species,
                      s=120,
                      alpha=0.8,
                      edgecolors='black',
                      linewidth=1.2,
                      zorder=5)

        # Set labels and title
        ax.set_xlabel('Longitude', fontsize=14, fontweight='bold')
        ax.set_ylabel('Latitude', fontsize=14, fontweight='bold')
        ax.set_title('Species Diversity Map', fontsize=18, fontweight='bold', pad=15)

        # Add legend
        legend = ax.legend(bbox_to_anchor=(1.02, 1),
                          loc='upper left',
                          fontsize=9,
                          framealpha=0.95,
                          edgecolor='black',
                          fancybox=True,
                          shadow=True)
        legend.set_title('Species', prop={'size': 10, 'weight': 'bold'})

        # Add text annotation with stats
        total_trees = len(data)
        num_species = len(unique_species)
        ax.text(0.02, 0.98,
                f'Total Trees: {total_trees}\nSpecies: {num_species}',
                transform=ax.transAxes,
                verticalalignment='top',
                bbox=dict(boxstyle='round', facecolor='white', alpha=0.9, edgecolor='black'),
                fontsize=10,
                fontweight='bold',
                zorder=6)

        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'diversity_static.png')
        plt.savefig(output_path, dpi=120, facecolor='white', bbox_inches='tight')
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ============================================================================
# Additional Analysis Models
# ============================================================================

@app.route('/analyze/height', methods=['POST'])
def analyze_height():
    """Height analysis bar chart"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        plt.figure(figsize=(14, 10))
        plt.bar(data['scientific_name'], data['height'], color='maroon')
        plt.xlabel('Scientific Name', fontsize=14)
        plt.ylabel('Height (m)', fontsize=14)
        plt.title('Tree Heights by Species', fontsize=18)
        plt.xticks(rotation=90)
        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'height.png')
        plt.savefig(output_path, dpi=150)
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/width', methods=['POST'])
def analyze_width():
    """Width analysis bar chart"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        # Calculate average width by species
        avg_widths = data.groupby('scientific_name')['width'].mean()

        plt.figure(figsize=(14, 10))
        plt.bar(avg_widths.index, avg_widths.values, color='green')
        plt.xlabel('Scientific Name', fontsize=14)
        plt.ylabel('Average Width (m)', fontsize=14)
        plt.title('Average Tree Width by Species', fontsize=18)
        plt.xticks(rotation=90)
        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'width.png')
        plt.savefig(output_path, dpi=150)
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/stats', methods=['POST'])
def analyze_stats():
    """Generate statistical summary"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        stats = {
            "total_trees": len(data),
            "total_species": data['scientific_name'].nunique(),
            "avg_height": float(data['height'].mean()),
            "avg_width": float(data['width'].mean()),
            "total_carbon": float(data['carbon_seq'].sum()),
            "most_common_species": data['scientific_name'].mode()[0] if len(data) > 0 else "N/A",
            "species_distribution": data['scientific_name'].value_counts().to_dict()
        }

        return jsonify(stats)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/summary', methods=['POST'])
def analyze_summary():
    """Generate comprehensive summary"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        summary = {
            "project_stats": {
                "total_trees": len(data),
                "unique_species": data['scientific_name'].nunique(),
                "avg_height_m": round(data['height'].mean(), 2),
                "avg_width_m": round(data['width'].mean(), 2),
                "total_carbon_kg_year": round(data['carbon_seq'].sum(), 2)
            },
            "top_species": data['scientific_name'].value_counts().head(5).to_dict(),
            "height_range": {
                "min": float(data['height'].min()),
                "max": float(data['height'].max()),
                "median": float(data['height'].median())
            },
            "carbon_leaders": data.nlargest(5, 'carbon_seq')[['scientific_name', 'carbon_seq']].to_dict('records')
        }

        return jsonify(summary)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    print("=" * 60)
    print("ArborTag Backend API with All Your Models + Map Backgrounds")
    print("=" * 60)
    print(f"Temporary directory: {TEMP_DIR}")
    print("\nAvailable endpoints:")
    print("  GET  /health")
    print("\n  Analysis Models:")
    print("  POST /analyze/distribution       - From python_distribution.py")
    print("  POST /analyze/heatmap            - Carbon heatmap (Interactive HTML)")
    print("  POST /analyze/heatmap_static     - Carbon heatmap (PNG with map)")
    print("  POST /analyze/diversity          - Diversity map (Interactive HTML)")
    print("  POST /analyze/diversity_static   - Diversity map (PNG with map)")
    print("  POST /analyze/height             - Height bar chart")
    print("  POST /analyze/width              - Width bar chart")
    print("  POST /analyze/stats              - Statistical summary")
    print("  POST /analyze/summary            - Comprehensive summary")
    print("=" * 60)
    app.run(debug=True, host='0.0.0.0', port=5000)