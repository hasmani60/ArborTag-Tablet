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
# MODEL 2: From python_heatmap_png1.py (Static PNG version for mobile)
# ============================================================================

@app.route('/analyze/heatmap_static', methods=['POST'])
def analyze_heatmap_static():
    """Carbon sequestration heatmap - PNG version for mobile display"""
    try:
        file = request.files['file']
        data = pd.read_csv(file)

        # Create figure with better sizing for mobile
        fig, ax = plt.subplots(figsize=(12, 10))

        # Normalize carbon values for color mapping
        norm = plt.Normalize(vmin=data['carbon_seq'].min(), vmax=data['carbon_seq'].max())

        # Create scatter plot with carbon sequestration as color
        scatter = ax.scatter(data['long'], data['lat'],
                            c=data['carbon_seq'],
                            s=data['carbon_seq']*8,  # Size based on carbon
                            cmap='RdYlGn_r',  # Red-Yellow-Green reversed
                            alpha=0.7,
                            edgecolors='black',
                            linewidth=0.8,
                            norm=norm)

        # Add colorbar
        cbar = plt.colorbar(scatter, ax=ax, pad=0.02)
        cbar.set_label('Carbon Sequestration (kg CO₂/year)', fontsize=12, fontweight='bold')

        # Set labels and title
        ax.set_xlabel('Longitude', fontsize=14, fontweight='bold')
        ax.set_ylabel('Latitude', fontsize=14, fontweight='bold')
        ax.set_title('Carbon Sequestration Heatmap', fontsize=18, fontweight='bold', pad=15)

        # Add grid
        ax.grid(True, alpha=0.3, linestyle='--', linewidth=0.5)

        # Add text annotation with stats
        total_carbon = data['carbon_seq'].sum()
        avg_carbon = data['carbon_seq'].mean()
        ax.text(0.02, 0.98,
                f'Total: {total_carbon:.1f} kg CO₂/yr\nAvg: {avg_carbon:.1f} kg CO₂/yr',
                transform=ax.transAxes,
                verticalalignment='top',
                bbox=dict(boxstyle='round', facecolor='white', alpha=0.8),
                fontsize=10)

        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'carbon_heatmap_static.png')
        plt.savefig(output_path, dpi=120, facecolor='white', bbox_inches='tight')
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ============================================================================
# MODEL 3: From python_diversity_png1.py (Static PNG version for mobile)
# ============================================================================

@app.route('/analyze/diversity_static', methods=['POST'])
def analyze_diversity_static():
    """Species diversity map - PNG version for mobile display"""
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

        # Plot each species with its assigned color
        for species in unique_species:
            species_data = data[data['scientific_name'] == species]
            ax.scatter(species_data['long'], species_data['lat'],
                      c=color_map[species],
                      label=species,
                      s=80,
                      alpha=0.7,
                      edgecolors='black',
                      linewidth=0.8)

        # Set labels and title
        ax.set_xlabel('Longitude', fontsize=14, fontweight='bold')
        ax.set_ylabel('Latitude', fontsize=14, fontweight='bold')
        ax.set_title('Species Diversity Map', fontsize=18, fontweight='bold', pad=15)

        # Add legend
        legend = ax.legend(bbox_to_anchor=(1.02, 1),
                          loc='upper left',
                          fontsize=9,
                          framealpha=0.95,
                          edgecolor='black')
        legend.set_title('Species', prop={'size': 10, 'weight': 'bold'})

        # Add grid
        ax.grid(True, alpha=0.3, linestyle='--', linewidth=0.5)

        # Add text annotation with stats
        total_trees = len(data)
        num_species = len(unique_species)
        ax.text(0.02, 0.98,
                f'Total Trees: {total_trees}\nSpecies: {num_species}',
                transform=ax.transAxes,
                verticalalignment='top',
                bbox=dict(boxstyle='round', facecolor='white', alpha=0.8),
                fontsize=10)

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
    print("ArborTag Backend API with All Your Models")
    print("=" * 60)
    print(f"Temporary directory: {TEMP_DIR}")
    print("\nAvailable endpoints:")
    print("  GET  /health")
    print("\n  Analysis Models:")
    print("  POST /analyze/distribution       - From python_distribution.py")
    print("  POST /analyze/heatmap_static     - Carbon heatmap (PNG)")
    print("  POST /analyze/diversity_static   - Diversity map (PNG)")
    print("  POST /analyze/height             - Height bar chart")
    print("  POST /analyze/width              - Width bar chart")
    print("  POST /analyze/stats              - Statistical summary")
    print("  POST /analyze/summary            - Comprehensive summary")
    print("=" * 60)
    app.run(debug=True, host='0.0.0.0', port=5000)